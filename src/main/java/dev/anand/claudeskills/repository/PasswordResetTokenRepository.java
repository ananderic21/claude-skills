package dev.anand.claudeskills.repository;

import dev.anand.claudeskills.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidate every still-usable token for a user. Called before issuing a new
     * one so a fresh "forgot password" request supersedes any earlier link.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.used = true where t.userId = :userId and t.used = false")
    void invalidateActiveTokens(@Param("userId") Long userId);
}
