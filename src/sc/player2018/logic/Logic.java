package sc.player2018.logic;

import java.security.SecureRandom;
import sc.player2018.RatedMove;
import java.util.ArrayList;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.player2018.Starter;
import sc.plugin2018.*;
import sc.plugin2018.util.Constants;
import sc.shared.PlayerColor;
import sc.shared.GameResult;
import sc.shared.InvalidGameStateException;
import sc.shared.InvalidMoveException;

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
	 * Erzeugt ein neues Strategieobjekt, das zufaellige Zuege taetigt.
	 *
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

	public int getMoveRating(Move move, GameState gamestate, int depth) {
		if (depth == 0) {
			return 0;
		}
		GameState gamestate_clone;
		try {
			gamestate_clone = gamestate.clone();
		} catch (CloneNotSupportedException e1) {
			// wtf, let's just ignore this
			return 0;
		}
		try {
			move.perform(gamestate_clone);
		} catch (InvalidMoveException e) {
			// Move is not valid, do not perform, disqualifies us.
			return -1;
		} catch (InvalidGameStateException e) {
			// wtf, let's just ignore this but better not perform this move
			return -1;
		}
		for (Move nextMove : gamestate_clone.getPossibleMoves()) {
			// check if the enemy can win for one of the moves that he can do afterwards
			boolean calculate = true;
			for (Action action : nextMove.getActions()) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					if (advance.getDistance()
							+ gamestate_clone.getCurrentPlayer().getFieldIndex() == Constants.NUM_FIELDS - 1) {
						// enemy can win after our move
						return -1;
					}
				}
				if (action instanceof FallBack) {
					calculate = false;
				}
				if (action instanceof Card) {
					Card card = (Card) action;
					if (card.getType() != CardType.TAKE_OR_DROP_CARROTS) {
						calculate = false;
					}
				}
			}
			if (calculate) {
				int rating = -getMoveRating(move, gamestate_clone, depth - 1);
				if (rating != 0) {
					return rating;
				}
			}
		}
		return 0;
	}

	private void prepareEnd(long startTime) {
		long nowTime = System.nanoTime();
		log.warn("Time needed for turn: {}", (nowTime - startTime) / 1000000);
	}

	public RatedMove getRatedMove(Move move) {
		// method is used if nothing else could be found or an emergency emerges
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				Advance advance = (Advance) action;
				if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
					// winning move
					return new RatedMove(move, Integer.MAX_VALUE);

				} else if (gameState.getBoard()
						.getTypeAt(advance.getDistance() + currentPlayer.getFieldIndex()) == FieldType.SALAD) {
					// advance to salad field
					return new RatedMove(move, 4);
				} else {
					int carrotsNeeded = (int) ((advance.getDistance() + 1) * ((float) advance.getDistance() / 2));
					int awayFromGoalAfter = Constants.NUM_FIELDS
							- (currentPlayer.getFieldIndex() + advance.getDistance() + 1);
					int carrotsNeededToGoal = (int) ((awayFromGoalAfter + 1) * ((float) awayFromGoalAfter / 2))
							+ carrotsNeeded;
					return new RatedMove(move, 10 - (currentPlayer.getCarrots() - carrotsNeededToGoal));
				}
			} else if (action instanceof Card) {
				Card card = (Card) action;
				if (card.getType() == CardType.EAT_SALAD) {
					// removing a salad on hare field can only be done once, lower value
					return new RatedMove(move, 3);
				}
			} else if (action instanceof ExchangeCarrots) {
				ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
				if (exchangeCarrots.getValue() == 10 && currentPlayer.getCarrots() < 30
						&& currentPlayer.getFieldIndex() < 40
						&& !(currentPlayer.getLastNonSkipAction() instanceof ExchangeCarrots)) {
					// do not take carrots in the end game
					return new RatedMove(move, Integer.MIN_VALUE);
				} else if (exchangeCarrots.getValue() == -10 && currentPlayer.getCarrots() > 30
						&& currentPlayer.getFieldIndex() >= 40) {
					// only remove carrots if at end
					return new RatedMove(move, 1);
				}
			} else if (action instanceof FallBack) {
				if (currentPlayer.getFieldIndex() > 56 /* last salad-field */ && currentPlayer.getSalads() > 0) {
					// fall back if you are at the end and have not given away all salads
					return new RatedMove(move, 3);
				} else if (currentPlayer.getFieldIndex() <= 56 && currentPlayer.getFieldIndex()
						- gameState.getPreviousFieldByType(FieldType.HEDGEHOG, currentPlayer.getFieldIndex()) < 5) {
					// never go back in end game
					return new RatedMove(move, Integer.MIN_VALUE);
				}
			} else if (action instanceof EatSalad) {
				// Eat salads you dumb shit
				return new RatedMove(move, 4);
			}
		}
		return new RatedMove(move, Integer.MIN_VALUE);
	}

	private int getNextUnoccupied(FieldType fieldType, int index) {
		int temp = gameState.getNextFieldByType(fieldType, currentPlayer.getFieldIndex());
		if (gameState.isOccupied(temp)) {
			return getNextUnoccupied(fieldType, temp);
		}
		return temp;
	}

	private boolean sendNextByType(FieldType fieldType, ArrayList<Move> possibleMoves) {
		int nextFieldOfType = getNextUnoccupied(fieldType, currentPlayer.getFieldIndex());
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					if (advance.getDistance() + currentPlayer.getFieldIndex() == nextFieldOfType) {
						move.orderActions();
						sendAction(move);
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean sendFallback(ArrayList<Move> possibleMoves) {
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof FallBack) {
					move.orderActions();
					sendAction(move);
					return true;
				}
			}
		}
		return false;
	}

	private boolean sendNextAdvance(ArrayList<Move> possibleMoves) {
		Move selectedMove = null;
		Advance selectedAdvance = null;
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					if (selectedAdvance == null) {
						selectedAdvance = advance;
						selectedMove = move;
						continue;
					}
					if (advance.getDistance() < selectedAdvance.getDistance()) {
						selectedAdvance = advance;
						selectedMove = move;
					}
				}
			}
		}
		if (selectedMove != null) {
			selectedMove.orderActions();
			sendAction(selectedMove);
			return true;
		}
		return false;
	}

	private boolean sendEatSalad(ArrayList<Move> possibleMoves) {
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof EatSalad) {
					move.orderActions();
					sendAction(move);
					return true;
				}
			}
		}
		return false;
	}

	private boolean sendHareEatSalad(ArrayList<Move> possibleMoves) {
		ArrayList<Move> saladMoves = new ArrayList<>();

		// select all moves that play out a salad card
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Card) {
					Card card = (Card) action;
					if (card.getType() == CardType.EAT_SALAD) {
						saladMoves.add(move);
					}
				}
			}
		}
		// select the nearest hare field
		int nextHareUnoccupied = getNextUnoccupied(FieldType.HARE, currentPlayer.getFieldIndex());
		for (Move move : saladMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					// check if this field is the nearest
					if (advance.getDistance() + currentPlayer.getFieldIndex() == nextHareUnoccupied) {
						move.orderActions();
						sendAction(move);
						return true;
					}
				}
			}
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
		ArrayList<RatedMove> ratedMoves = new ArrayList<>();

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
						prepareEnd(startTime);
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
			if (selectedMove != null) {
				selectedMove.orderActions();
				sendAction(selectedMove);
				prepareEnd(startTime);
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
					if (sendEatSalad(possibleMoves)) {
						prepareEnd(startTime);
						return;
					}
				} else {
					if (currentPlayer.getFieldIndex() >= 22) {
						// go back, you have salads to eat!
						if (sendFallback(possibleMoves)) {
							prepareEnd(startTime);
							return;
						}
					} else {
						if (!gameState.isOccupied(22)) { // 22 is OUR salad field
							if (sendNextByType(FieldType.SALAD, possibleMoves)) {
								prepareEnd(startTime);
								return;
							}
						} else {
							if (sendNextAdvance(possibleMoves)) {
								prepareEnd(startTime);
								return;
							}
						}
					}
				}
			} else {
				if (currentPlayer.getSalads() > 4) {
					// let's waste some turns in the beginning to lose a salad and wait for the
					// enemy to move away
					if (sendHareEatSalad(possibleMoves)) {
						prepareEnd(startTime);
						return;
					}
				} else {
					// can we move to the next salad field?
					if (gameState
							.isOccupied(gameState.getNextFieldByType(FieldType.SALAD, currentPlayer.getFieldIndex()))) {
						if (sendNextAdvance(possibleMoves)) {
							prepareEnd(startTime);
							return;
						}
					} else {
						// move to the salad field
						if (sendNextByType(FieldType.SALAD, possibleMoves)) {
							prepareEnd(startTime);
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
							for(Action inner_action: move.actions) {
								if(inner_action instanceof Card) {
									hasCard = true;
									break;
								}
							}
							if(hasCard) {
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
				if (selectedMoves[2] != null) {
					selectedMoves[2].orderActions();
					sendAction(selectedMoves[2]);
					prepareEnd(startTime);
					return;
				}
			} else {
				// end-game
				if (currentPlayer.getFieldIndex() > 56) {
					for (Move move : possibleMoves) {
						if (this.getMoveRating(move, gameState, 7) == 1) {
							move.orderActions();
							sendAction(move);
							prepareEnd(startTime);
							return;
						}
					}
				} else {
					for (Move move : possibleMoves) {
						if (this.getMoveRating(move, gameState, 4) == 1) {
							move.orderActions();
							sendAction(move);
							prepareEnd(startTime);
							return;
						}
					}
				}
			}
		}

		for (Move move : possibleMoves) {
			ratedMoves.add(getRatedMove(move));
		}
		RatedMove selectedMove = new RatedMove();
		if (ratedMoves.size() < 1) {
			selectedMove = new RatedMove(possibleMoves.get(rand.nextInt(possibleMoves.size())));
		} else {
			for (RatedMove move : ratedMoves) {
				if (move.getRating() > selectedMove.getRating()) {
					selectedMove = move;
				}
			}
		}
		selectedMove.orderActions();
		log.info("Sende zug {}", selectedMove);

		sendAction(selectedMove.getMove());
		prepareEnd(startTime);
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
