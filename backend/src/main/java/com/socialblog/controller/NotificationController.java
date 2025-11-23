package com.socialblog.controller;

import com.socialblog.dto.UserDTO;
import com.socialblog.model.entity.Notification;
import com.socialblog.model.entity.User;
import com.socialblog.repository.UserRepository;
import com.socialblog.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * GET /notifications - Hiển thị trang thông báo
     */
    @GetMapping
    public String notificationsPage(HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO currentUserDTO = (UserDTO) session.getAttribute("currentUser");

        if (currentUserDTO == null) {
            ra.addFlashAttribute("error", "Bạn cần đăng nhập");
            return "redirect:/auth/login";
        }

        try {
            User currentUser = userRepository.findById(currentUserDTO.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

            List<Notification> notifications = notificationService.getUserNotifications(currentUser);
            int unreadCount = notificationService.getUnreadCount(currentUser);

            model.addAttribute("notifications", notifications);
            model.addAttribute("unreadCount", unreadCount);
            model.addAttribute("currentUser", currentUserDTO);
            model.addAttribute("pageTitle", "Thông báo");

            log.info("✅ User {} đã xem {} thông báo", currentUser.getUsername(), notifications.size());
            return "Notification/index";
        } catch (Exception e) {
            log.error("❌ Lỗi khi tải trang thông báo: {}", e.getMessage());
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/";
        }
    }

    /**
     * ✅ GET /notifications/unread - Lấy thông báo (API JSON)
     */
    @GetMapping("/unread")
    @ResponseBody
    public Map<String, Object> getUnreadNotifications(HttpSession session) {
        log.info("🔔 API /notifications/unread được gọi");

        UserDTO currentUserDTO = (UserDTO) session.getAttribute("currentUser");

        if (currentUserDTO == null) {
            log.warn("⚠️ currentUser là null");
            return Map.of("success", false, "error", "Cần đăng nhập", "notifications", List.of());
        }

        try {
            log.info("👤 Lấy user với id: {}", currentUserDTO.getId());
            User currentUser = userRepository.findById(currentUserDTO.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

            List<Notification> notifications = notificationService.getUserNotifications(currentUser);
            int unreadCount = notificationService.getUnreadCount(currentUser);

            log.info("✅ Lấy {} thông báo cho {}", notifications.size(), currentUser.getUsername());

            // ✅ Tạo response với thông tin cơ bản (tránh lazy loading)
            var notifList = notifications.stream()
                    .map(n -> Map.of(
                            "id", n.getId(),
                            "message", n.getMessage() != null ? n.getMessage() : "",
                            "type", n.getType().toString(),
                            "read", n.isRead(),
                            "createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "",
                            "postId", n.getPost() != null ? n.getPost().getId() : 0  // ✅ THÊM DÒNG NÀY
                    ))
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("notifications", notifList);
            response.put("unreadCount", unreadCount);

            return response;

        } catch (Exception e) {
            log.error("❌ Lỗi khi lấy thông báo: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage(), "notifications", List.of());
        }
    }

    /**
     * GET /notifications/unread/count - Đếm thông báo chưa đọc
     */
    @GetMapping("/unread/count")
    @ResponseBody
    public Map<String, Object> getUnreadCount(HttpSession session) {
        UserDTO currentUserDTO = (UserDTO) session.getAttribute("currentUser");
        if (currentUserDTO == null) {
            return Map.of("success", false, "count", 0);
        }

        try {
            User currentUser = userRepository.findById(currentUserDTO.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

            int count = notificationService.getUnreadCount(currentUser);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            log.error("❌ Lỗi: {}", e.getMessage());
            return Map.of("success", false, "count", 0);
        }
    }

    /**
     * POST /notifications/{id}/read - Đánh dấu một thông báo đã đọc
     */
    @PostMapping("/{id}/read")
    public String markAsRead(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            notificationService.markAsRead(id);
            log.info("✅ Đánh dấu thông báo {} đã đọc", id);
            ra.addFlashAttribute("success", "Đánh dấu đã đọc thành công");
        } catch (Exception e) {
            log.error("❌ Lỗi khi đánh dấu đã đọc: {}", e.getMessage());
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/notifications";
    }

    /**
     * POST /notifications/{id}/delete - Xóa một thông báo
     */
    @PostMapping("/{id}/delete")
    public String deleteNotification(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        try {
            notificationService.deleteNotification(id);
            log.info("✅ Xóa thông báo {}", id);
            ra.addFlashAttribute("success", "Xóa thông báo thành công");
        } catch (Exception e) {
            log.error("❌ Lỗi khi xóa thông báo: {}", e.getMessage());
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/notifications";
    }

    /**
     * POST /notifications/read-all - Đánh dấu tất cả thông báo đã đọc
     */
    @PostMapping("/read-all")
    public String markAllAsRead(HttpSession session, RedirectAttributes ra) {
        try {
            UserDTO currentUserDTO = (UserDTO) session.getAttribute("currentUser");
            User currentUser = userRepository.findById(currentUserDTO.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

            List<Notification> unread = notificationService.getUnreadNotifications(currentUser);
            for (Notification n : unread) {
                notificationService.markAsRead(n.getId());
            }

            log.info("✅ Đánh dấu {} thông báo của {} đã đọc", unread.size(), currentUser.getUsername());
            ra.addFlashAttribute("success", "Đánh dấu tất cả đã đọc thành công");
        } catch (Exception e) {
            log.error("❌ Lỗi khi đánh dấu tất cả đã đọc: {}", e.getMessage());
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/notifications";
    }
}