package sc.player2018.logic;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.player2018.Starter;
import sc.plugin2018.*;
import sc.plugin2018.util.Constants;
import sc.shared.PlayerColor;
import sc.shared.GameResult;

/**
 * Logic of the simple client we are building
 */
public class Logic implements IGameHandler {

	private Starter client;
	private GameState gameState;
	private Player currentPlayer;

	private static final Logger log = LoggerFactory.getLogger(Logic.class);
	private static final Random rand = new SecureRandom();

	public Logic(Starter client) {
		this.client = client;
	}

	public void gameEnded(GameResult data, PlayerColor color, String errorMessage) {
		log.info("Das Spiel ist beendet.");
	}

	private boolean endIfPossible(Move move, long startTime) {
		if (move != null) {
			move.orderActions();
			sendAction(move);
			LogicHelper.prepareEnd(startTime, log);
			return true;
		}
		return false;
	}

	@Override
	public void onRequestAction() {
		int currentIndex = currentPlayer.getFieldIndex();
		long startTime = System.nanoTime();
		log.info("Es wurde ein Zug angefordert.");
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();
		// debugging
		if (gameState.getRound() == 0) {
			System.out.println("We are color: " + currentPlayer.getPlayerColor());
		}

		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					if (advance.getDistance() + currentIndex == Constants.NUM_FIELDS - 1) {
						// winning move
						move.orderActions();
						sendAction(move);
						LogicHelper.prepareEnd(startTime, log);
						return;
					}
				}
			}
		}
		if (gameState.getRound() == Constants.ROUND_LIMIT - 2) {
			if (endIfPossible(LogicHelper.getLastAdvance(possibleMoves), startTime)) {
				return;
			}
		}

		if (currentPlayer.getSalads() > 0) {
			if (currentIndex < 10) {
				// before field 10 is early-game
				if(endIfPossible(EarlyGameLogic.getTurn(gameState),startTime)) {
					return;
				}
			} else {
				if(endIfPossible(MidGameLogic.getTurn(gameState),startTime)) {
					return;
				}
			}
		} else {
			// there are no salads left, we are in end-game
			if (endIfPossible(EndGameLogic.getTurn(gameState), startTime)) {
				return;
			}
		}

		log.warn("Falling back to simple logic");
		Move defaultMove = LogicHelper.getSimpleMove(possibleMoves, gameState, currentPlayer);
		defaultMove.orderActions();
		sendAction(defaultMove);
		LogicHelper.prepareEnd(startTime, log);
	}

	@Override
	public void onUpdate(Player player, Player otherPlayer) {
		currentPlayer = player;
		log.info("Spielerwechsel: " + player.getPlayerColor());
	}

	@Override
	public void onUpdate(GameState gameState) {
		this.gameState = gameState;
		currentPlayer = gameState.getCurrentPlayer();
		log.info("Das Spiel geht voran: Zug: {}", gameState.getTurn());
		log.info("Spieler: {}", currentPlayer.getPlayerColor());
	}

	@Override
	public void sendAction(Move move) {
		if (move.actions.size() < 1) {
			log.error("EMERGENCY MOVE");
			log.error("Had {} selected", move.toString());
			move = gameState.getPossibleMoves().get(rand.nextInt(gameState.getPossibleMoves().size()));
		}
		client.sendMove(move);
	}
}