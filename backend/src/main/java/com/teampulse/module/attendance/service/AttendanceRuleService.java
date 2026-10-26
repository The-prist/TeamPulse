package com.teampulse.module.attendance.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.attendance.dto.AttendanceDtos;
import com.teampulse.module.attendance.entity.AttendanceRule;
import com.teampulse.module.attendance.mapper.AttendanceRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 考勤规则服务。
 *
 * <p>当前为「全局单条生效」模型：取 id 最小且 enabled=1 的规则。
 * 若要按部门差异化，可给 rule 增加 team_id / dept_id 并在此处按用户所属组织匹配。
 */
@Service
@RequiredArgsConstructor
public class AttendanceRuleService {

    private final AttendanceRuleMapper ruleMapper;

    /** 取当前生效规则；没有配置时抛业务异常，避免下游拿 null 继续算 */
    public AttendanceRule currentRule() {
        AttendanceRule rule = ruleMapper.selectOne(Wrappers.<AttendanceRule>lambdaQuery()
                .eq(AttendanceRule::getEnabled, 1)
                .orderByAsc(AttendanceRule::getId)
                .last("LIMIT 1"));
        if (rule == null) {
            throw BizException.of(ErrorCode.ATTENDANCE_RULE_MISSING);
        }
        return rule;
    }

    public AttendanceRule getOrNull() {
        return ruleMapper.selectOne(Wrappers.<AttendanceRule>lambdaQuery()
                .eq(AttendanceRule::getEnabled, 1)
                .orderByAsc(AttendanceRule::getId)
                .last("LIMIT 1"));
    }

    @Transactional
    public AttendanceRule update(AttendanceDtos.RuleUpdateRequest request) {
        if (!request.getWorkStart().isBefore(request.getWorkEnd())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "上班时间必须早于下班时间");
        }
        AttendanceRule rule = getOrNull();
        if (rule == null) {
            rule = new AttendanceRule();
            rule.setRuleName("标准工作制");
            rule.setEnabled(1);
        }
        rule.setWorkStart(request.getWorkStart());
        rule.setWorkEnd(request.getWorkEnd());
        rule.setLateGraceMinutes(request.getLateGraceMinutes());
        rule.setEarlyGraceMinutes(request.getEarlyGraceMinutes());
        rule.setWorkDays(request.getWorkDays());

        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return rule;
    }
}
