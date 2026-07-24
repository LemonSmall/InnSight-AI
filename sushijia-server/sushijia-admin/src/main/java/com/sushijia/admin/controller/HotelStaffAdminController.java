package com.sushijia.admin.controller;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.R;
import com.sushijia.common.response.ResultCode;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.HotelStaff;
import com.sushijia.repository.entity.Tenant;
import com.sushijia.repository.mapper.HotelStaffMapper;
import com.sushijia.repository.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/staff")
@RequiredArgsConstructor
public class HotelStaffAdminController {

    private final TenantMapper tenantMapper;
    private final HotelStaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public R<List<HotelStaff>> list(@PathVariable("tenantId") Long tenantId) {
        ensureTenantExists(tenantId);
        List<HotelStaff> staff = staffMapper.findByTenantId(tenantId);
        staff.forEach(item -> item.setPasswordHash(null));
        return R.ok(staff);
    }

    @PostMapping
    public R<HotelStaff> create(@PathVariable("tenantId") Long tenantId, @RequestBody Map<String, Object> body) {
        ensureTenantExists(tenantId);
        String phone = required(body, "phone");
        ensurePhoneAvailable(phone, null);

        HotelStaff staff = new HotelStaff();
        staff.setTenantId(tenantId);
        staff.setName(required(body, "name"));
        staff.setPhone(phone);
        staff.setRole(String.valueOf(body.getOrDefault("role", "manager")));
        staff.setAvatar(String.valueOf(body.getOrDefault("avatar", "")));
        staff.setPasswordHash(passwordEncoder.encode(String.valueOf(body.getOrDefault("password", "123456"))));
        withTenant(tenantId, () -> staffMapper.insert(staff));
        staff.setPasswordHash(null);
        return R.ok(staff);
    }

    @PutMapping("/{staffId}")
    public R<HotelStaff> update(@PathVariable("tenantId") Long tenantId,
                                @PathVariable("staffId") Long staffId,
                                @RequestBody Map<String, Object> body) {
        HotelStaff staff = loadStaff(tenantId, staffId);
        if (body.containsKey("name")) {
            staff.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("role")) {
            staff.setRole(String.valueOf(body.get("role")));
        }
        if (body.containsKey("avatar")) {
            staff.setAvatar(String.valueOf(body.get("avatar")));
        }
        withTenant(tenantId, () -> staffMapper.updateById(staff));
        staff.setPasswordHash(null);
        return R.ok(staff);
    }

    @PutMapping("/{staffId}/phone")
    public R<Map<String, Object>> changePhone(@PathVariable("tenantId") Long tenantId,
                                              @PathVariable("staffId") Long staffId,
                                              @RequestBody Map<String, Object> body) {
        HotelStaff staff = loadStaff(tenantId, staffId);
        String phone = required(body, "phone");
        ensurePhoneAvailable(phone, staffId);
        staff.setPhone(phone);
        withTenant(tenantId, () -> staffMapper.updateById(staff));
        return R.ok(Map.of("staffId", staffId, "phone", phone));
    }

    @PutMapping("/{staffId}/password")
    public R<Map<String, Object>> resetPassword(@PathVariable("tenantId") Long tenantId,
                                                @PathVariable("staffId") Long staffId,
                                                @RequestBody Map<String, Object> body) {
        HotelStaff staff = loadStaff(tenantId, staffId);
        String password = String.valueOf(body.getOrDefault("password", "123456"));
        if (password.length() < 6) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码至少 6 位");
        }
        staff.setPasswordHash(passwordEncoder.encode(password));
        withTenant(tenantId, () -> staffMapper.updateById(staff));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("staffId", staffId);
        data.put("message", "密码已重置");
        return R.ok(data);
    }

    @DeleteMapping("/{staffId}")
    public R<String> delete(@PathVariable("tenantId") Long tenantId, @PathVariable("staffId") Long staffId) {
        loadStaff(tenantId, staffId);
        withTenant(tenantId, () -> staffMapper.deleteById(staffId));
        return R.ok("ok");
    }

    private void ensureTenantExists(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.TENANT_NOT_FOUND);
        }
    }

    private HotelStaff loadStaff(Long tenantId, Long staffId) {
        HotelStaff staff = staffMapper.findByIdIgnoreTenant(staffId);
        if (staff == null || !tenantId.equals(staff.getTenantId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return staff;
    }

    private void ensurePhoneAvailable(String phone, Long currentStaffId) {
        HotelStaff existing = staffMapper.findByPhone(phone);
        if (existing != null && (currentStaffId == null || !currentStaffId.equals(existing.getId()))) {
            throw new BizException(ResultCode.CONFLICT, "手机号已被其他酒店账号使用");
        }
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, key + "不能为空");
        }
        return String.valueOf(value).trim();
    }

    private void withTenant(Long tenantId, Runnable action) {
        TenantContext.set(tenantId);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
