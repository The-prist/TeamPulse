package com.teampulse.module.sky.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.module.sky.dto.SkyDtos;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录页天空上下文：访客 IP → 大致位置 → 当前天气。
 *
 * <p><b>隐私</b>：原始 IP 只在单次请求的内存里用于查询；落缓存时用的是
 * 「进程启动时随机生成的盐 + SHA-256」的摘要，进程重启即失效，
 * 既不写数据库也无法回放出 IP。
 *
 * <p><b>降级</b>：任何外部调用失败（超时 / 非 200 / 解析失败）都回落到
 * 北京 + 多云，保证登录页永远有可用数据。
 */
@Slf4j
@Service
public class SkyContextService {

    private static final String DEFAULT_PROVINCE = "北京市";
    private static final String DEFAULT_CITY = "北京市";
    private static final double DEFAULT_LAT = 39.9042;
    private static final double DEFAULT_LON = 116.4074;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /** 每次进程启动随机生成，仅用于对 IP 做不可逆摘要 */
    private final String ipSalt = UUID.randomUUID().toString();

    /** IP 归属地：摘要 → 结果，30 分钟 */
    private final TtlCache<Geo> geoCache = new TtlCache<>(Duration.ofMinutes(30));
    /** 天气：经纬度取整 → 天气，10 分钟（上游本身就是 15 分钟步长） */
    private final TtlCache<Current> weatherCache = new TtlCache<>(Duration.ofMinutes(10));
    /** 地名 → 坐标，24 小时 */
    private final TtlCache<double[]> coordCache = new TtlCache<>(Duration.ofHours(24));
    /** 地名 → 候选列表（同名地点很多），24 小时 */
    private final TtlCache<List<Cand>> candidateCache = new TtlCache<>(Duration.ofHours(24));

    // ------------------------------------------------------------------ 对外入口

    public SkyDtos.SkyContext resolve(HttpServletRequest request,
                                      String province, String city, String district) {
        Geo ipGeo = lookupIp(request);
        String ipCity = ipGeo == null ? null : ipGeo.displayName();

        boolean manual = isNotBlank(city) || isNotBlank(district);
        double lat;
        double lon;
        String source;

        if (manual) {
            source = "manual";
            double[] coords = resolveCoords(province, city, district);
            if (coords == null) {
                coords = ipGeo != null
                        ? new double[]{ipGeo.lat(), ipGeo.lon()}
                        : new double[]{DEFAULT_LAT, DEFAULT_LON};
                source = "fallback";
            }
            lat = coords[0];
            lon = coords[1];
        } else if (ipGeo != null) {
            source = "ip";
            lat = ipGeo.lat();
            lon = ipGeo.lon();
        } else {
            source = "fallback";
            lat = DEFAULT_LAT;
            lon = DEFAULT_LON;
        }

        Current current = lookupWeather(lat, lon);

        String effProvince = isNotBlank(province) ? province
                : (ipGeo != null && isNotBlank(ipGeo.region()) ? ipGeo.region() : DEFAULT_PROVINCE);
        String effCity = isNotBlank(city) ? city
                : (ipGeo != null && isNotBlank(ipGeo.city()) ? ipGeo.city() : DEFAULT_CITY);

        return new SkyDtos.SkyContext(
                source,
                ipCity,
                effProvince,
                effCity,
                isNotBlank(district) ? district : null,
                round(lat),
                round(lon),
                current == null ? 50 : current.cloudCover(),
                current == null ? 3 : current.weatherCode(),
                current == null ? 0.0 : current.precipitation(),
                current == null ? null : current.temperature(),
                current == null ? 1 : current.isDay(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    // ------------------------------------------------------------------ IP → 位置

    private Geo lookupIp(HttpServletRequest request) {
        String ip = clientIp(request);
        if (ip == null) {
            return null;
        }
        String key = digest(ip);
        Geo cached = geoCache.get(key);
        if (cached != null) {
            return cached;
        }
        Geo geo = fetchIpGeo(ip);
        if (geo != null) {
            geoCache.put(key, geo);
        }
        return geo;
    }

    private String clientIp(HttpServletRequest request) {
        String real = trimToNull(request.getHeader("X-Real-IP"));
        if (real != null && !isPrivate(real)) {
            return real;
        }
        String forwarded = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            for (String part : forwarded.split(",")) {
                String candidate = trimToNull(part);
                if (candidate != null && !isPrivate(candidate)) {
                    return candidate;
                }
            }
        }
        String remote = trimToNull(request.getRemoteAddr());
        return remote == null || isPrivate(remote) ? null : remote;
    }

    private Geo fetchIpGeo(String ip) {
        try {
            JsonNode node = getJson("https://ipwho.is/" + ip + "?lang=zh-CN");
            if (node == null || !node.path("success").asBoolean(false)) {
                return null;
            }
            double lat = node.path("latitude").asDouble();
            double lon = node.path("longitude").asDouble();
            if (lat == 0 && lon == 0) {
                return null;
            }
            return new Geo(
                    node.path("city").asText(null),
                    node.path("region").asText(null),
                    node.path("country").asText(null),
                    lat,
                    lon);
        } catch (Exception e) {
            log.debug("IP 归属地查询失败 err={}", e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ 地名 → 坐标

    private double[] resolveCoords(String province, String city, String district) {
        String key = String.join("/", nz(province), nz(city), nz(district));
        double[] cached = coordCache.get(key);
        if (cached != null) {
            return cached;
        }

        // ① 城市坐标（「市辖区」这类占位名跳过，退到省）
        double[] cityCoords = null;
        for (String name : new String[]{city, province}) {
            if (!isNotBlank(name) || isGenericCityLevel(name)) {
                continue;
            }
            cityCoords = bestForPlace(name, province, city);
            if (cityCoords != null) {
                break;
            }
        }

        // ② 区县：全国同名很多，取离所属城市最近的那个；超过 150km 视为同名异地
        double[] districtCoords = null;
        if (isNotBlank(district)) {
            districtCoords = nearestTo(district, cityCoords);
        }

        double[] result = districtCoords != null ? districtCoords : cityCoords;
        if (result != null) {
            coordCache.put(key, result);
        }
        return result;
    }

    /** 「市辖区」「县」这类占位名不是真实地名 */
    private static boolean isGenericCityLevel(String name) {
        return switch (name) {
            case "市辖区", "县", "省直辖县级行政区划", "自治区直辖县级行政区划", "直辖县级行政区划" -> true;
            default -> false;
        };
    }

    /** 去行政后缀：杭州市 → 杭州（实测 geocoder 只认后者，查「杭州市」返回空） */
    private static String stripSuffix(String name) {
        if (name == null) {
            return null;
        }
        String[] suffixes = {"特别行政区", "自治区", "自治州", "自治县", "自治旗", "地区", "新区", "省", "市", "区", "县", "盟", "旗"};
        String out = name.trim();
        for (String suffix : suffixes) {
            if (out.length() > suffix.length() + 1 && out.endsWith(suffix)) {
                return out.substring(0, out.length() - suffix.length());
            }
        }
        return out;
    }

    /** 在候选里挑最可能的：先看行政区划名能否对上，再看人口 */
    private double[] bestForPlace(String name, String province, String city) {
        List<Cand> cands = candidates(name);
        if (cands.isEmpty()) {
            return null;
        }
        String targetProvince = stripSuffix(province);
        String targetCity = stripSuffix(city);
        Cand best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Cand c : cands) {
            double score = 0;
            if (isNotBlank(targetProvince) && c.matchesAdmin(targetProvince)) {
                score += 5e8;
            }
            if (isNotBlank(targetCity) && c.matchesAdmin(targetCity)) {
                score += 8e8;
            }
            score += c.population();
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best == null ? null : new double[]{best.lat(), best.lon()};
    }

    private double[] nearestTo(String district, double[] cityCoords) {
        List<Cand> cands = candidates(district);
        if (cands.isEmpty()) {
            return null;
        }
        if (cityCoords == null) {
            return new double[]{cands.get(0).lat(), cands.get(0).lon()};
        }
        Cand best = null;
        double bestDist = Double.MAX_VALUE;
        for (Cand c : cands) {
            double d = haversine(cityCoords[0], cityCoords[1], c.lat(), c.lon());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        if (best == null || bestDist > 150) {
            return null;
        }
        return new double[]{best.lat(), best.lon()};
    }

    /** 先按原名查，查不到再剥后缀重试 */
    private List<Cand> candidates(String name) {
        String key = "cand|" + name;
        List<Cand> cached = candidateCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Cand> out = new ArrayList<>();
        for (String candidate : new String[]{name, stripSuffix(name)}) {
            if (!isNotBlank(candidate) || candidate.length() < 2) {
                continue;
            }
            out.addAll(queryGeocoder(candidate));
            if (!out.isEmpty()) {
                break;
            }
        }
        candidateCache.put(key, out);
        return out;
    }

    private List<Cand> queryGeocoder(String name) {
        List<Cand> out = new ArrayList<>();
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "&count=10&language=zh&format=json&countryCode=CN";
            JsonNode node = getJson(url);
            JsonNode results = node == null ? null : node.path("results");
            if (results == null || !results.isArray()) {
                return out;
            }
            for (JsonNode r : results) {
                if (!r.path("latitude").isNumber() || !r.path("longitude").isNumber()) {
                    continue;
                }
                out.add(new Cand(
                        r.path("name").asText(""),
                        r.path("admin1").asText(""),
                        r.path("admin2").asText(""),
                        r.path("admin3").asText(""),
                        r.path("latitude").asDouble(),
                        r.path("longitude").asDouble(),
                        r.path("population").asDouble(0)));
            }
        } catch (Exception e) {
            log.debug("地名解析失败 name={} err={}", name, e.toString());
        }
        return out;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    // ------------------------------------------------------------------ 天气

    private Current lookupWeather(double lat, double lon) {
        String key = String.format("%.2f,%.2f", lat, lon);
        Current cached = weatherCache.get(key);
        if (cached != null) {
            return cached;
        }
        Current current = fetchWeather(lat, lon);
        if (current != null) {
            weatherCache.put(key, current);
        }
        return current;
    }

    private Current fetchWeather(double lat, double lon) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=cloud_cover,weather_code,precipitation,temperature_2m,is_day"
                    + "&timezone=Asia%2FShanghai";
            JsonNode node = getJson(url);
            JsonNode cur = node == null ? null : node.path("current");
            if (cur == null || cur.isMissingNode()) {
                return null;
            }
            return new Current(
                    cur.path("cloud_cover").isNumber() ? cur.path("cloud_cover").asInt() : 50,
                    cur.path("weather_code").isNumber() ? cur.path("weather_code").asInt() : 3,
                    cur.path("precipitation").isNumber() ? cur.path("precipitation").asDouble() : 0.0,
                    cur.path("temperature_2m").isNumber() ? cur.path("temperature_2m").asDouble() : null,
                    cur.path("is_day").isNumber() ? cur.path("is_day").asInt() : 1);
        } catch (Exception e) {
            log.debug("天气查询失败 lat={} lon={} err={}", lat, lon, e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ 工具

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "TeamPulse-Sky/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                return null;
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            log.debug("外部请求失败 url={} err={}", url, e.toString());
            return null;
        }
    }

    private String digest(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((ipSalt + "|" + ip).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(ip.hashCode());
        }
    }

    private static boolean isPrivate(String ip) {
        if (ip == null) {
            return true;
        }
        String v = ip.trim();
        if (v.startsWith("127.") || v.startsWith("10.") || v.startsWith("192.168.") || v.startsWith("169.254.")
                || "::1".equals(v) || "0:0:0:0:0:0:0:1".equals(v)
                || v.toLowerCase().startsWith("fe80:") || v.toLowerCase().startsWith("fc") || v.toLowerCase().startsWith("fd")) {
            return true;
        }
        if (v.startsWith("172.")) {
            String[] parts = v.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private record Geo(String city, String region, String country, double lat, double lon) {
        String displayName() {
            return isNotBlank(city) ? city : region;
        }
    }

    private record Current(int cloudCover, int weatherCode, double precipitation, Double temperature, int isDay) {
    }

    /** geocoder 的一个候选地点 */
    private record Cand(String name, String admin1, String admin2, String admin3,
                        double lat, double lon, double population) {
        /** 行政区划名是否对得上（geocoder 返回的 admin 可能中英混排） */
        boolean matchesAdmin(String target) {
            if (!isNotBlank(target)) {
                return false;
            }
            for (String field : new String[]{name, admin1, admin2, admin3}) {
                if (isNotBlank(field) && (field.contains(target) || target.contains(field))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 极简 TTL 缓存：键空间很小，不做容量淘汰，超量直接清空 */
    private static final class TtlCache<V> {
        private record Entry<V>(V value, long expireAt) {
        }

        private final Map<String, Entry<V>> map = new ConcurrentHashMap<>();
        private final long ttlMillis;

        TtlCache(Duration ttl) {
            this.ttlMillis = ttl.toMillis();
        }

        V get(String key) {
            Entry<V> e = map.get(key);
            if (e == null) {
                return null;
            }
            if (System.currentTimeMillis() > e.expireAt()) {
                map.remove(key);
                return null;
            }
            return e.value();
        }

        void put(String key, V value) {
            if (map.size() > 5000) {
                map.clear();
            }
            map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
        }
    }
}
