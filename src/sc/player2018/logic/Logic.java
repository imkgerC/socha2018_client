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
	/*
	 * Klassenweit verfuegbarer Zufallsgenerator der beim Laden der klasse einmalig
	 * erzeugt wird und darn immer zur Verfuegung steht.
	 */
	private static final Random rand = new SecureRandom();

	/**
	 * @param client
	 *            Der Zugrundeliegende Client der mit dem Spielserver kommunizieren
	 *            kann.
	 */
	public Logic(Starter client) {
		this.client = client;
	}

	/**
	 * {@inheritDoc}
	 */
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onRequestAction() {
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
					if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
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
			Advance selectedAdvance = null;
			Move selectedMove = null;
			for (Move move : possibleMoves) {
				for (Action action : move.actions) {
					if (action instanceof Advance) {
						Advance advance = (Advance) action;
						if (selectedAdvance == null) {
							selectedAdvance = advance;
							selectedMove = move;
							continue;
						}
						if (advance.getDistance() > selectedAdvance.getDistance()) {
							selectedAdvance = advance;
							selectedMove = move;
						}
					}
				}
			}
			if (endIfPossible(selectedMove, startTime)) {
				return;
			}
		}

		if (currentPlayer.getSalads() > 0) {
			/*
			 * Johannes' strategy for the start: Move past the first salad field but use it
			 * and waste a turn there (sometimes) Then lose all salads at the second salad
			 * field.
			 */
			if (currentPlayer.getFieldIndex() >= 10) {
				// if we can eat a salad, we should
				if (gameState.getTypeAt(currentPlayer.getFieldIndex()) == FieldType.SALAD
						&& !(currentPlayer.getLastNonSkipAction() instanceof EatSalad)) {
					if (endIfPossible(LogicHelper.getEatSalad(possibleMoves), startTime)) {
						return;
					}
				} else {
					if (currentPlayer.getFieldIndex() >= 22) {
						// go back, you have salads to eat!
						if (endIfPossible(LogicHelper.getFallback(possibleMoves), startTime)) {
							return;
						}
					} else {
						if (!gameState.isOccupied(22)) { // 22 is OUR salad field
							if (endIfPossible(LogicHelper.getNextByType(FieldType.SALAD, possibleMoves,gameState,currentPlayer),startTime)) {
								return;
							}
						} else {
							if(currentPlayer.getFieldIndex() == 15) {
								if(endIfPossible(LogicHelper.getNextByType(FieldType.POSITION_2, possibleMoves, gameState, currentPlayer),startTime)) {
									return;
								}
							}
							if (endIfPossible(LogicHelper.getFallback(possibleMoves), startTime)) {
								return;
							}
							if (endIfPossible(LogicHelper.getNextAdvance(possibleMoves), startTime)) {
								return;
							}
						}
					}
				}
			} else {
				if (currentPlayer.getSalads() > 4) {
					// let's waste some turns in the beginning to lose a salad and wait for the
					// enemy to move away
					if (endIfPossible(LogicHelper.getHareEatSalad(possibleMoves, gameState, currentPlayer),
							startTime)) {
						LogicHelper.prepareEnd(startTime, log);
						return;
					}
				} else {
					// can we move to the next salad field?
					if (gameState
							.isOccupied(gameState.getNextFieldByType(FieldType.SALAD, currentPlayer.getFieldIndex()))) {
						if (endIfPossible(LogicHelper.getNextAdvance(possibleMoves), startTime)) {
							return;
						}
					} else {
						// move to the salad field
						if (endIfPossible(LogicHelper.getNextByType(FieldType.SALAD, possibleMoves,gameState,currentPlayer),startTime)) {
							return;
						}
					}
				}
			}
		} else {
			// taking the third most far away field should be okay
			if (currentPlayer.getFieldIndex() < 47) {
				int[] threeHighest = { 0, 0, 0 };
				Move[] selectedMoves = { null, null, null };
				for (Move move : possibleMoves) {
					for (Action action : move.actions) {
						if (action instanceof Advance) {
							boolean hasCard = false;
							for (Action inner_action : move.actions) {
								if (inner_action instanceof Card) {
									hasCard = true;
									break;
								}
							}
							if (hasCard) {
								continue;
							}
							Advance advance = (Advance) action;
							if (advance.getDistance() > threeHighest[0]) {
								threeHighest[2] = threeHighest[1];
								threeHighest[1] = threeHighest[0];
								threeHighest[0] = advance.getDistance();
								selectedMoves[2] = selectedMoves[1];
								selectedMoves[1] = selectedMoves[0];
								selectedMoves[0] = move;
							} else if (advance.getDistance() > threeHighest[1]) {
								threeHighest[2] = threeHighest[1];
								threeHighest[1] = advance.getDistance();
								selectedMoves[2] = selectedMoves[1];
								selectedMoves[1] = move;
							} else if (advance.getDistance() > threeHighest[2]) {
								threeHighest[2] = advance.getDistance();
								selectedMoves[2] = move;
							}
						}
					}
				}
				if (endIfPossible(selectedMoves[2], startTime)) {
					return;
				}
			} else {
				// end-game
				if (currentPlayer.getFieldIndex() > 56) {
					if (endIfPossible(LogicHelper.getWinningMove(possibleMoves, 7, gameState, startTime), startTime)) {
						return;
					}
				} else {
					if (endIfPossible(LogicHelper.getSimpleEndMove(possibleMoves, gameState, currentPlayer), startTime)) {
						return;
					}
				}
			}
		}

		log.warn("Falling back to simple logic");
		Move defaultMove = LogicHelper.getSimpleMove(possibleMoves, gameState, currentPlayer);
		defaultMove.orderActions();
		sendAction(defaultMove);
		LogicHelper.prepareEnd(startTime, log);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(Player player, Player otherPlayer) {
		currentPlayer = player;
		log.info("Spielerwechsel: " + player.getPlayerColor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(GameState gameState) {
		this.gameState = gameState;
		currentPlayer = gameState.getCurrentPlayer();
		log.info("Das Spiel geht voran: Zug: {}", gameState.getTurn());
		log.info("Spieler: {}", currentPlayer.getPlayerColor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendAction(Move move) {
		if (move.actions.size() < 1) {
			log.warn("EMERGENCY MOVE");
			move = gameState.getPossibleMoves().get(rand.nextInt(gameState.getPossibleMoves().size()));
		}
		client.sendMove(move);
	}

}
