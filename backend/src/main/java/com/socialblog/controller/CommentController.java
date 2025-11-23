package com.socialblog.controller;

import com.socialblog.dto.CommentRequest;
import com.socialblog.dto.UserDTO;
import com.socialblog.model.entity.User;
import com.socialblog.repository.UserRepository;
import com.socialblog.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CommentController {

    private final UserRepository userRepository;
    private final CommentService commentService;

    @PostMapping("/comment/add")
    public String addComment(CommentRequest request, HttpSession session, RedirectAttributes ra) {

        UserDTO userDTO = (UserDTO) session.getAttribute("currentUser");
        if (userDTO == null) {
            ra.addFlashAttribute("error", "Bạn cần đăng nhập");
            return "redirect:/auth/login";
        }

        try {
            // Truy vấn User entity thật từ DB
            User author = userRepository.findById(userDTO.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ✅ Gọi commentService.addComment() để trigger notification
            commentService.addComment(request, author);

            ra.addFlashAttribute("success", "Thêm bình luận thành công");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }

        return "redirect:/post/" + request.getPostId();
    }

    @PostMapping("/comment/{id}/delete")
    public String deleteComment(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes ra,
            HttpServletRequest request) {
        UserDTO currentUser = (UserDTO) session.getAttribute("currentUser");

        if (currentUser == null) {
            ra.addFlashAttribute("error", "Bạn cần đăng nhập");
            return "redirect:/auth/login";
        }

        try {
            commentService.deleteComment(id, currentUser.getId());
            ra.addFlashAttribute("success", "Xóa bình luận thành công");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }

}