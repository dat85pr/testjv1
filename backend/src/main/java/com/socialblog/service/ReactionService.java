package com.socialblog.service;

import com.socialblog.dto.ReactionRequest;
import com.socialblog.model.entity.Post;
import com.socialblog.model.entity.Reaction;
import com.socialblog.model.entity.User;
import com.socialblog.model.enums.ReactionType;
import com.socialblog.repository.PostRepository;
import com.socialblog.repository.ReactionRepository;
import com.socialblog.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactionService {
    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    /**
     * Thêm hoặc cập nhật reaction
     * - Nếu chưa có reaction → Tạo mới
     * - Nếu đã có reaction khác → Đổi sang reaction mới
     * - Nếu click lại reaction cũ → Xóa reaction (toggle)
     */
    @Transactional
    public long addOrUpdateReaction(ReactionRequest request, User user) {

        // Tìm post
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết!"));

        // Tìm reaction hiện tại của user cho post này
        Reaction existingReaction = reactionRepository
                .findByPostAndUser(post, user)
                .orElse(null);

        if (existingReaction != null) {
            // Đã có reaction
            if (existingReaction.getType() == request.getType()) {
                // Click lại reaction cũ → XÓA (toggle off)
                log.info("🗑️ Removing existing reaction (toggle off)");
                reactionRepository.delete(existingReaction);
            } else {
                // Đổi sang reaction khác
                log.info("🔄 Changing reaction from {} to {}",
                        existingReaction.getType(), request.getType());
                existingReaction.setType(request.getType());
                reactionRepository.save(existingReaction);
                // ✅ TẠO THÔNG BÁO KHI ĐỔIMREACTION (chỉ 1 lần)
                notificationService.createReactionNotification(post.getAuthor(), user, post);
            }
        } else {
            // Chưa có reaction → Tạo mới
            log.info("➕ Creating new reaction");
            Reaction newReaction = Reaction.builder()
                    .post(post)
                    .user(user)
                    .type(request.getType())
                    .build();
            reactionRepository.save(newReaction);
            // ✅ TẠO THÔNG BÁO KHI REACTION MỚI (chỉ 1 lần)
            notificationService.createReactionNotification(post.getAuthor(), user, post);
        }

        // Cập nhật tổng số reaction của post
        long totalReactions = reactionRepository.countByPost(post);
        post.setLikeCount((int) totalReactions);
        postRepository.save(post);

        log.info("✅ Reaction processed - Total reactions: {}", totalReactions);

        return totalReactions;
    }

    /**
     * Xóa reaction
     */
    @Transactional
    public long removeReaction(Long postId, User user) {

        log.info("🗑️ Removing reaction - User: {}, Post: {}", user.getId(), postId);

        // Tìm post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết!"));

        // Tìm và xóa reaction
        reactionRepository.findByPostAndUser(post, user)
                .ifPresent(reaction -> {
                    log.info("🗑️ Deleting reaction type: {}", reaction.getType());
                    reactionRepository.delete(reaction);
                });

        // Cập nhật tổng số reaction của post
        long totalReactions = reactionRepository.countByPost(post);
        post.setLikeCount((int) totalReactions);
        postRepository.save(post);

        log.info("✅ Reaction removed - Total reactions: {}", totalReactions);

        return totalReactions;
    }

    /**
     * Đếm tổng số reaction của một post
     */
    public long countReactionsByPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết!"));
        return reactionRepository.countByPost(post);
    }

    /**
     * Lấy reaction của user cho một post
     */
    public String getUserReactionForPost(Long postId, User user) {
        return reactionRepository.findReactionType(postId, user.getId())
                .orElse(null);
    }

    /**
     * Thống kê top các loại reaction trên một bài post
     */
    public List<ReactionCount> topReactions(Post post, int limit) {
        return reactionRepository.countGroupByType(post.getId()).stream()
                .map(row -> new ReactionCount((ReactionType) row[0], (Long) row[1]))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .limit(limit)
                .toList();
    }

    public record ReactionCount(ReactionType type, long count) {
    }

}