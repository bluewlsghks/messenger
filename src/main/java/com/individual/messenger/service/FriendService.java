package com.individual.messenger.service;

import com.individual.messenger.domain.Friend;
import com.individual.messenger.dto.FriendDto;
import com.individual.messenger.repo.FriendRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FriendService {

    private final FriendRepository friendRepo;

    public FriendService(FriendRepository friendRepo) {
        this.friendRepo = friendRepo;
    }

    public void addFriend(String me, String peer) {
        if (me == null || peer == null || me.isBlank() || peer.isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID");
        }
        if (me.equalsIgnoreCase(peer)) {
            throw new IllegalArgumentException("자기 자신은 추가할 수 없습니다.");
        }
        if (friendRepo.existsByOwnerIdAndFriendId(me, peer)) {
            return; // 이미 친구
        }
        friendRepo.save(new Friend(me, peer, LocalDateTime.now()));
        friendRepo.save(new Friend(peer, me, LocalDateTime.now())); // 양방향 저장
    }

    public List<FriendDto> list(String me) {
        return friendRepo.findByOwnerId(me).stream()
                .map(f -> new FriendDto(f.getFriendId(), f.getFriendId())) // userName 연동 시 수정
                .collect(Collectors.toList());
    }
}
