package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.lobby.QuickGameLobby
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public-facing browse endpoint for quick-game lobbies. Mirrors [TournamentController.listPublic]
 * so the home screen can merge tournaments and quick games into a single "Public Lobbies" list.
 *
 * AI lobbies and full lobbies are excluded — there's no second seat to join.
 */
@RestController
@RequestMapping("/api/quick-games")
class QuickGameController(
    private val lobbyRepository: QuickGameLobbyRepository
) {

    data class PublicQuickGameDTO(
        val lobbyId: String,
        val playerCount: Int,
        val maxPlayers: Int,
        val setCode: String?,
        val hostName: String?
    )

    @GetMapping("/public")
    fun listPublic(): ResponseEntity<List<PublicQuickGameDTO>> {
        val publicLobbies = lobbyRepository.findAll()
            .filter { lobby -> lobby.isPublic && !lobby.vsAi && !lobby.started && !lobby.isFull }
            .sortedBy { it.lobbyId }
            .map { lobby ->
                val host = lobby.players.firstOrNull { !it.isAi }
                PublicQuickGameDTO(
                    lobbyId = lobby.lobbyId,
                    playerCount = lobby.players.count { !it.isAi },
                    maxPlayers = QuickGameLobby.MAX_PLAYERS,
                    setCode = lobby.setCode ?: host?.setCode,
                    hostName = host?.playerName
                )
            }
        return ResponseEntity.ok(publicLobbies)
    }
}
