package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.MatchHistoryDto;
import com.utn.pokemontcg.api.dto.RankingEntryDto;
import com.utn.pokemontcg.application.service.MatchSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ranking")
@Tag(name = "Ranking", description = "Match history and player ranking")
public class RankingController {

    private final MatchSessionService matchSessionService;

    public RankingController(MatchSessionService matchSessionService) {
        this.matchSessionService = matchSessionService;
    }

    @GetMapping
    public List<RankingEntryDto> getRanking() {
        List<MatchSessionService.MatchMeta> summaries = matchSessionService.listSummaries();
        Map<String, int[]> stats = new HashMap<>(); // username -> [wins, losses]

        for (MatchSessionService.MatchMeta m : summaries) {
            if (!"FINISHED".equals(m.status())) continue;
            String winner = matchSessionService.getWinnerUsername(m.id()).orElse(null);
            if (m.player1Username() != null) stats.computeIfAbsent(m.player1Username(), k -> new int[2]);
            if (m.player2Username() != null) stats.computeIfAbsent(m.player2Username(), k -> new int[2]);
            if (winner != null) {
                stats.computeIfAbsent(winner, k -> new int[2])[0]++;
                String loser = winner.equals(m.player1Username()) ? m.player2Username() : m.player1Username();
                if (loser != null) stats.computeIfAbsent(loser, k -> new int[2])[1]++;
            }
        }

        List<RankingEntryDto> ranking = new ArrayList<>();
        stats.forEach((username, wl) ->
                ranking.add(new RankingEntryDto(0, username, wl[0], wl[1], wl[0] + wl[1])));
        ranking.sort((a, b) -> b.wins() - a.wins());

        List<RankingEntryDto> ranked = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            RankingEntryDto e = ranking.get(i);
            ranked.add(new RankingEntryDto(i + 1, e.username(), e.wins(), e.losses(), e.totalGames()));
        }
        return ranked;
    }

    @GetMapping("/history/{username}")
    public List<MatchHistoryDto> getHistory(@PathVariable String username) {
        return matchSessionService.listSummaries().stream()
                .filter(m -> username.equals(m.player1Username()) || username.equals(m.player2Username()))
                .map(m -> {
                    String opponent = username.equals(m.player1Username()) ? m.player2Username() : m.player1Username();
                    String result;
                    if (!"FINISHED".equals(m.status())) {
                        result = "IN_PROGRESS";
                    } else {
                        String winner = matchSessionService.getWinnerUsername(m.id()).orElse(null);
                        result = username.equals(winner) ? "WIN" : "LOSS";
                    }
                    return new MatchHistoryDto(m.id(), opponent, result, m.createdAt());
                })
                .toList();
    }
}
