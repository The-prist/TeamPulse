package com.teampulse.module.captcha.service;

import com.teampulse.module.captcha.dto.CaptchaDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑块拼图验证码（服务端出题 + 服务端校验）。
 *
 * <p><b>为什么放服务端</b>：答案（缺口 x 坐标）只在服务端保存，
 * 前端拿到的是两张已渲染好的 PNG（带缺口的底图 + 拼图块），
 * 想通过就必须真的做一次图像配准 —— 比纯前端校验难得多。
 *
 * <p><b>流程</b>
 * <ol>
 *   <li>{@code GET /api/v1/public/captcha} → 返回 token + 两张图的 data URI</li>
 *   <li>用户拖动滑块，前端把偏移量连同 token 提交到
 *       {@code POST /api/v1/public/captcha/verify}</li>
 *   <li>校验通过 → 返回一次性 ticket（HMAC 签名，3 分钟有效）</li>
 *   <li>登录时带上 ticket，服务端验签并作废</li>
 * </ol>
 */
@Slf4j
@Service
public class CaptchaService {

    /** 底图尺寸 */
    static final int WIDTH = 320;
    static final int HEIGHT = 160;
    /** 拼图块半径 */
    static final int PIECE_R = 20;
    /** 允许误差（像素） */
    static final int TOLERANCE = 6;

    /** 题目有效期 */
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(3);
    /** ticket 有效期 */
    private static final Duration TICKET_TTL = Duration.ofMinutes(3);

    private final SecureRandom random = new SecureRandom();
    /** 每次启动随机生成，用于给 ticket 签名 */
    private final byte[] ticketSecret = new byte[32];

    /** token → 题目（答案只在服务端） */
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    /** 已经用过的 ticket → 过期时间戳（一次性） */
    private final Map<String, Long> usedTickets = new ConcurrentHashMap<>();

    public CaptchaService() {
        random.nextBytes(ticketSecret);
    }

    // ------------------------------------------------------------------ 出题

    public CaptchaDtos.Challenge issue() {
        cleanup();

        int holeX = PIECE_R + 24 + random.nextInt(WIDTH - PIECE_R * 2 - 48);
        int holeY = 36 + random.nextInt(HEIGHT - 72);

        BufferedImage pattern = drawPattern();
        BufferedImage withHole = drawHole(pattern, holeX, holeY);
        BufferedImage piece = cropPiece(pattern, holeX, holeY);

        String token = UUID.randomUUID().toString().replace("-", "");
        challenges.put(token, new Challenge(holeX - PIECE_R, System.currentTimeMillis() + CHALLENGE_TTL.toMillis(), false));

        return new CaptchaDtos.Challenge(
                token,
                WIDTH,
                HEIGHT,
                PIECE_R,
                holeY - PIECE_R,
                WIDTH - PIECE_R * 2,
                toDataUri(withHole),
                toDataUri(piece)
        );
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 校验滑块偏移。
     *
     * @return 通过则返回一次性 ticket；不通过返回 null
     */
    public String verify(String token, int offsetX) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Challenge challenge = challenges.get(token);
        if (challenge == null) {
            return null;
        }
        if (System.currentTimeMillis() > challenge.expireAt() || challenge.used()) {
            challenges.remove(token);
            return null;
        }
        // 无论成败，一道题只判一次，防止反复试探
        challenges.remove(token);

        if (Math.abs(offsetX - challenge.answerX()) > TOLERANCE) {
            log.debug("验证码未对齐: token={} 提交={} 答案={}", token, offsetX, challenge.answerX());
            return null;
        }
        return issueTicket();
    }

    /** 签发一次性 ticket */
    private String issueTicket() {
        long expireAt = System.currentTimeMillis() + TICKET_TTL.toMillis();
        String payload = String.valueOf(expireAt);
        String ticket = payload + "." + sign(payload);
        return ticket;
    }

    /**
     * 消费 ticket（一次性）。
     *
     * @return true 表示 ticket 合法且此前未被使用
     */
    public boolean consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        int dot = ticket.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        String payload = ticket.substring(0, dot);
        String signature = ticket.substring(dot + 1);
        if (!constantTimeEquals(signature, sign(payload))) {
            return false;
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            return false;
        }
        // 一次性：放进已用集合，重复提交直接失败
        Long previous = usedTickets.putIfAbsent(ticket, expireAt);
        return previous == null;
    }

    // ------------------------------------------------------------------ 绘图

    private BufferedImage drawPattern() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 渐变底
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(31, 95, 168),
                    WIDTH, HEIGHT, new Color(55, 179, 216)));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 柔光圆斑
            for (int i = 0; i < 12; i++) {
                int x = random.nextInt(WIDTH);
                int y = random.nextInt(HEIGHT);
                int r = 10 + random.nextInt(44);
                g.setColor(new Color(255, 255, 255, 10 + random.nextInt(26)));
                g.fill(new Ellipse2D.Double(x - r, y - r, r * 2.0, r * 2.0));
            }
            // 斜纹
            for (int i = 0; i < 10; i++) {
                int x = random.nextInt(WIDTH);
                g.setColor(new Color(255, 255, 255, 12 + random.nextInt(20)));
                g.setStroke(new BasicStroke(1f + random.nextFloat() * 2f));
                g.drawLine(x, 0, x - 30 + random.nextInt(60), HEIGHT);
            }
            // 几何图形（给缺口周围提供参照物）
            for (int i = 0; i < 5; i++) {
                g.setColor(new Color(255, 255, 255, 56 + random.nextInt(64)));
                g.setStroke(new BasicStroke(2f));
                if (i % 2 == 0) {
                    int r = 14 + random.nextInt(26);
                    int x = random.nextInt(WIDTH);
                    int y = random.nextInt(HEIGHT);
                    g.draw(new Ellipse2D.Double(x - r, y - r, r * 2.0, r * 2.0));
                } else {
                    int x = random.nextInt(WIDTH);
                    int y = random.nextInt(HEIGHT);
                    g.draw(new Rectangle2D.Double(x - 20, y - 14, 40 + random.nextInt(30), 28 + random.nextInt(20)));
                }
            }
            // 噪点
            for (int i = 0; i < 240; i++) {
                g.setColor(new Color(255, 255, 255, random.nextInt(26)));
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** 在底图上挖出缺口 */
    private BufferedImage drawHole(BufferedImage pattern, int holeX, int holeY) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(pattern, 0, 0, null);
            g.setColor(new Color(4, 16, 30, 158));
            g.fill(new Ellipse2D.Double(holeX - PIECE_R, holeY - PIECE_R, PIECE_R * 2.0, PIECE_R * 2.0));
            g.setColor(new Color(255, 255, 255, 190));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Ellipse2D.Double(holeX - PIECE_R, holeY - PIECE_R, PIECE_R * 2.0, PIECE_R * 2.0));
        } finally {
            g.dispose();
        }
        return image;
    }

    /** 从原图裁出拼图块（圆形，透明背景） */
    private BufferedImage cropPiece(BufferedImage pattern, int holeX, int holeY) {
        int size = PIECE_R * 2;
        BufferedImage piece = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new Ellipse2D.Double(0, 0, size, size));
            g.drawImage(pattern, -holeX + PIECE_R, -holeY + PIECE_R, null);
            g.setClip(null);
            g.setColor(new Color(255, 255, 255, 230));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(1, 1, size - 2.0, size - 2.0));
        } finally {
            g.dispose();
        }
        return piece;
    }

    private String toDataUri(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("验证码图片编码失败: {}", e.toString());
            return "";
        }
    }

    // ------------------------------------------------------------------ 工具

    private String sign(String payload) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(ticketSecret, "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", raw[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("ticket 签名失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(e -> e.getValue().expireAt() < now);
        usedTickets.entrySet().removeIf(e -> e.getValue() < now);
        if (challenges.size() > 20000) {
            challenges.clear();
        }
    }

    private record Challenge(int answerX, long expireAt, boolean used) {
    }
}
