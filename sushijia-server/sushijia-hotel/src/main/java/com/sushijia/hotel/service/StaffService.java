package com.sushijia.hotel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import com.sushijia.framework.tenant.TenantContext;
import com.sushijia.repository.entity.HotelStaff;
import com.sushijia.repository.mapper.HotelStaffMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffService {

    private final HotelStaffMapper staffMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 获取当前租户的员工列表 */
    public List<HotelStaff> listStaff() {
        Long tenantId = TenantContext.get();
        return staffMapper.selectList(
            new LambdaQueryWrapper<HotelStaff>()
                .eq(HotelStaff::getTenantId, tenantId)
                .orderByAsc(HotelStaff::getCreatedAt));
    }

    /** 新增员工 */
    @Transactional
    public HotelStaff createStaff(String name, String phone, String role) {
        Long tenantId = TenantContext.get();
        // 检查手机号不重复
        HotelStaff exist = staffMapper.findByPhone(phone);
        if (exist != null && exist.getTenantId().equals(tenantId)) {
            throw new BizException(ResultCode.CONFLICT);
        }

        HotelStaff staff = new HotelStaff();
        staff.setTenantId(tenantId);
        staff.setName(name);
        staff.setPhone(phone);
        staff.setRole(role);
        staff.setAvatar("");
        staff.setPasswordHash(passwordEncoder.encode("123456")); // 默认密码
        staffMapper.insert(staff);
        return staff;
    }

    /** 更新员工 */
    @Transactional
    public void updateStaff(Long staffId, String name, String phone, String role) {
        Long tenantId = TenantContext.get();
        HotelStaff staff = staffMapper.selectById(staffId);
        if (staff == null || !staff.getTenantId().equals(tenantId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (name != null) staff.setName(name);
        if (phone != null) staff.setPhone(phone);
        if (role != null) staff.setRole(role);
        staffMapper.updateById(staff);
    }

    /** 删除员工 */
    @Transactional
    public void deleteStaff(Long staffId) {
        Long tenantId = TenantContext.get();
        HotelStaff staff = staffMapper.selectById(staffId);
        if (staff == null || !staff.getTenantId().equals(tenantId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        staffMapper.deleteById(staffId);
    }

    /** 获取个人信息 */
    public HotelStaff getProfile(Long staffId) {
        HotelStaff staff = staffMapper.selectById(staffId);
        if (staff == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return staff;
    }

    /** 修改个人信息 */
    @Transactional
    public HotelStaff updateProfile(Long staffId, String name, String phone, String avatar) {
        HotelStaff staff = staffMapper.selectById(staffId);
        if (staff == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (name != null) staff.setName(name);
        if (phone != null) staff.setPhone(phone);
        if (avatar != null) staff.setAvatar(avatar);
        staffMapper.updateById(staff);
        return staff;
    }

    /** 修改密码 */
    @Transactional
    public void changePassword(Long staffId, String oldPassword, String newPassword) {
        HotelStaff staff = staffMapper.selectById(staffId);
        if (staff == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        // 验证旧密码（兼容 bcrypt 和 开发模式默认密码 123456）
        boolean oldMatch = staff.getPasswordHash() != null
            && passwordEncoder.matches(oldPassword, staff.getPasswordHash());
        boolean devMatch = "123456".equals(oldPassword)
            && staff.getPasswordHash() != null
            && passwordEncoder.matches("123456", staff.getPasswordHash());
        if (!oldMatch && !devMatch) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staffMapper.updateById(staff);
    }

}
