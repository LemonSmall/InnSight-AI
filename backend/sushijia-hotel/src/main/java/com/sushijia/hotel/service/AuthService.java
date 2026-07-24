package com.sushijia.hotel.service;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.common.utils.JwtUtil;
import com.sushijia.repository.entity.HotelStaff;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.HotelStaffMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final HotelStaffMapper staffMapper;
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 开发模式：发送的短信验证码固定为 123456，但登录仍必须校验验证码 */
    private static final boolean DEV_MODE = true;

    public void sendSmsCode(String phone) {
        String code = DEV_MODE ? "123456" : String.valueOf((int)(Math.random() * 900000) + 100000);
        log.info("【短信验证码】phone={}, code={}", phone, code);
        redisTemplate.opsForValue().set("sms:" + phone, code, 5, TimeUnit.MINUTES);
    }

    @Transactional(readOnly = true)
    public Map<String, String> loginByPhone(String phone, String code) {
        String savedCode = redisTemplate.opsForValue().get("sms:" + phone);
        if (savedCode == null) throw new BizException(ResultCode.SMS_CODE_EXPIRED);
        if (!savedCode.equals(code)) throw new BizException(ResultCode.LOGIN_FAILED);

        HotelStaff staff = staffMapper.findByPhone(phone);
        if (staff == null) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        Tenant tenant = tenantMapper.selectById(staff.getTenantId());
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }
        if ("suspended".equals(tenant.getStatus()) || "closed".equals(tenant.getStatus())) {
            throw new BizException(ResultCode.TENANT_SUSPENDED);
        }

        String accessToken = JwtUtil.createAccessToken(
                tenant.getId(), staff.getId(), staff.getRole(), null);
        String refreshToken = JwtUtil.createRefreshToken(
                tenant.getId(), staff.getId(), staff.getRole(), null);

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("role", staff.getRole());
        result.put("name", staff.getName());
        result.put("phone", staff.getPhone());
        result.put("staffId", String.valueOf(staff.getId()));
        result.put("tenantId", String.valueOf(tenant.getId()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, String> loginByPassword(String phone, String password) {
        HotelStaff staff = staffMapper.findByPhone(phone);
        if (staff == null || staff.getPasswordHash() == null || !passwordEncoder.matches(password, staff.getPasswordHash())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        Tenant tenant = tenantMapper.selectById(staff.getTenantId());
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }
        if ("suspended".equals(tenant.getStatus()) || "closed".equals(tenant.getStatus())) {
            throw new BizException(ResultCode.TENANT_SUSPENDED);
        }

        String accessToken = JwtUtil.createAccessToken(
                tenant.getId(), staff.getId(), staff.getRole(), null);
        String refreshToken = JwtUtil.createRefreshToken(
                tenant.getId(), staff.getId(), staff.getRole(), null);

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("role", staff.getRole());
        result.put("name", staff.getName());
        result.put("phone", staff.getPhone());
        result.put("staffId", String.valueOf(staff.getId()));
        result.put("tenantId", String.valueOf(tenant.getId()));
        return result;
    }

    public Map<String, String> refreshAccessToken(String refreshToken) {
        Map<String, Object> claims = JwtUtil.parseToken(refreshToken);
        if (claims == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        Long tenantId = ((Number) claims.get("tenant_id")).longValue();
        Long staffId = ((Number) claims.get("staff_id")).longValue();
        String role = (String) claims.get("role");

        String newAccessToken = JwtUtil.createAccessToken(tenantId, staffId, role, null);

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        return result;
    }
}
