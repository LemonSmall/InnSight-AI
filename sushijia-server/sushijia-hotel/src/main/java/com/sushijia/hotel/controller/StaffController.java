package com.sushijia.hotel.controller;

import com.sushijia.common.response.R;
import com.sushijia.hotel.service.StaffService;
import com.sushijia.repository.entity.HotelStaff;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 酒店端 - 员工管理 / 个人信息 / 密码修改
 */
@RestController
@RequestMapping("/api/hotel")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    // ====== 辅助方法：从 JWT 中获取当前员工 ID ======
    private Long getStaffId(HttpServletRequest request) {
        Object sid = request.getAttribute("staffId");
        if (sid instanceof Long) return (Long) sid;
        if (sid instanceof Number) return ((Number) sid).longValue();
        throw new RuntimeException("未登录");
    }

    /** 员工列表 */
    @GetMapping("/staff")
    public R<List<HotelStaff>> listStaff() {
        return R.ok(staffService.listStaff());
    }

    /** 新增员工 */
    @PostMapping("/staff")
    public R<HotelStaff> createStaff(@RequestBody Map<String, String> body) {
        HotelStaff staff = staffService.createStaff(
            body.get("name"),
            body.get("phone"),
            body.get("role"),
            body.get("password"));
        return R.ok(staff);
    }

    /** 更新员工 */
    @PutMapping("/staff/{id}")
    public R<String> updateStaff(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        staffService.updateStaff(id, body.get("name"), body.get("phone"), body.get("role"));
        return R.ok("更新成功");
    }

    /** 删除员工 */
    @DeleteMapping("/staff/{id}")
    public R<String> deleteStaff(@PathVariable("id") Long id) {
        staffService.deleteStaff(id);
        return R.ok("删除成功");
    }

    /** 管理员重置员工密码 */
    @PutMapping("/staff/{id}/password")
    public R<String> resetStaffPassword(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        staffService.resetPassword(id, body.get("newPassword"));
        return R.ok("密码已重置");
    }

    /** 获取个人信息 */
    @GetMapping("/profile")
    public R<Map<String, Object>> getProfile(HttpServletRequest request) {
        Long staffId = getStaffId(request);
        HotelStaff staff = staffService.getProfile(staffId);
        Map<String, Object> result = new HashMap<>();
        result.put("id", staff.getId());
        result.put("name", staff.getName());
        result.put("phone", staff.getPhone());
        result.put("role", staff.getRole());
        result.put("avatar", staff.getAvatar());
        return R.ok(result);
    }

    /** 修改个人信息 */
    @PutMapping("/profile")
    public R<Map<String, Object>> updateProfile(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long staffId = getStaffId(request);
        HotelStaff staff = staffService.updateProfile(
            staffId,
            body.get("name"),
            body.get("phone"),
            body.get("avatar"));
        Map<String, Object> result = new HashMap<>();
        result.put("id", staff.getId());
        result.put("name", staff.getName());
        result.put("phone", staff.getPhone());
        result.put("role", staff.getRole());
        result.put("avatar", staff.getAvatar());
        return R.ok(result);
    }

    /** 修改密码 */
    @PutMapping("/password")
    public R<String> changePassword(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long staffId = getStaffId(request);
        staffService.changePassword(
            staffId,
            body.get("oldPassword"),
            body.get("newPassword"));
        return R.ok("密码修改成功");
    }
}
