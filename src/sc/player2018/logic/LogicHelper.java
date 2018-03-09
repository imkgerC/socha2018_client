package sc.player2018.logic;

import java.security.SecureRandom;
import sc.player2018.RatedMove;
import java.util.ArrayList;
import java.util.Random;

import org.slf4j.Logger;
import sc.plugin2018.*;
import sc.plugin2018.util.Constants;
import sc.shared.InvalidGameStateException;
import sc.shared.InvalidMoveException;

public class LogicHelper {

	private static final int ms_to_nano_factor = 1000000;
	private static final Random rand = new SecureRandom();

	public static void prepareEnd(long startTime, Logger log) {
		// simple info message
		long nowTime = System.nanoTime();
		log.warn("Time needed for turn:" + (nowTime - startTime) / ms_to_nano_factor); // printed in warn, as info
																						// mostly isn't shown
		return;
	}

	public static int milliTimeLeft(long startTime) {
		// 2000ms is the total time available to us
		int timeLeft = 2000 - (int) (System.nanoTime() - startTime / ms_to_nano_factor);
		if (timeLeft > 0) {
			return timeLeft;
		}
		return 0;
	}

	public static boolean timeEnough(long startTime) {
		// we shouldn't send too late, as late garbage collection or another issue would
		// disqualify us
		return milliTimeLeft(startTime) > 200;
	}

	private static int getNextUnoccupied(FieldType fieldType, int index, int depth, GameState gameState,
			Player currentPlayer) {
		// recursive function as fields can be occupied or the gameState can bug around
		int temp = gameState.getNextFieldByType(fieldType, currentPlayer.getFieldIndex());
		if (temp < 0 || depth >= 20) {
			// preventing stack overflow
			return -1;
		}
		if (gameState.isOccupied(temp)) {
			// return next field after this one
			return getNextUnoccupied(fieldType, temp, depth + 1, gameState, currentPlayer);
		}
		return temp;
	}

	public static int getNextUnoccupied(FieldType fieldType, int index, GameState gameState, Player currentPlayer) {
		return getNextUnoccupied(fieldType, index, 0, gameState, currentPlayer);
	}

	public static Move getFallback(ArrayList<Move> possibleMoves) {
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof FallBack) {
					// there can only be one field we can FallBack onto, so send this
					return move;
				}
			}
		}
		return null;
	}

	public static Move getEatSalad(ArrayList<Move> possibleMoves) {
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof EatSalad) {
					// there can only be one move to eat a salad
					return move;
				}
			}
		}
		return null;
	}

	public static RatedMove getRatedMove(Move move, GameState gameState, Player currentPlayer) {
		// method is used if nothing else could be found or an emergency emerges
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				Advance advance = (Advance) action;
				if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
					// winning move
					return new RatedMove(move, Integer.MAX_VALUE);

				} else if (gameState.getBoard()
						.getTypeAt(advance.getDistance() + currentPlayer.getFieldIndex()) == FieldType.SALAD) {
					// advance to a salad field
					return new RatedMove(move, 4);
				} else {
					// complicated formula for calculating some semi-random bullcrap
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
					return new RatedMove(move, 3);
				}
			} else if (action instanceof ExchangeCarrots) {
				ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
				if (exchangeCarrots.getValue() == 10 && currentPlayer.getCarrots() < 30
						&& currentPlayer.getFieldIndex() < 56
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
					// fall back if you are at the end and have not eaten all the salads
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

	public static RatedMove getEndRatedMove(Move move, GameState gameState, Player currentPlayer) {
		// method is used if nothing else could be found or an emergency emerges
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				Advance advance = (Advance) action;
				if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
					// winning move
					return new RatedMove(move, Integer.MAX_VALUE);

				} else {
					// complicated formula for calculating some semi-random bullcrap
					int carrotsNeeded = (int) ((advance.getDistance() + 1) * ((float) advance.getDistance() / 2));
					int awayFromGoalAfter = Constants.NUM_FIELDS
							- (currentPlayer.getFieldIndex() + advance.getDistance() + 1);
					int carrotsNeededToGoal = (int) ((awayFromGoalAfter + 1) * ((float) awayFromGoalAfter / 2))
							+ carrotsNeeded;
					int carrotsLeftAfter = (currentPlayer.getCarrots() - carrotsNeededToGoal);
					if (carrotsLeftAfter < 10 && carrotsLeftAfter > 0) {
						if (gameState
								.getTypeAt(currentPlayer.getFieldIndex() + advance.getDistance()) == FieldType.CARROT) {
							return new RatedMove(move, 10);
						}
					}
					return new RatedMove(move, advance.getDistance());
				}
			} else if (action instanceof ExchangeCarrots) {
				ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
				if (exchangeCarrots.getValue() == 10 && currentPlayer.getCarrots() < 30
						&& currentPlayer.getFieldIndex() < 56
						&& !(currentPlayer.getLastNonSkipAction() instanceof ExchangeCarrots)) {
					// do not take carrots in end game
					return new RatedMove(move, 1);
				} else if (exchangeCarrots.getValue() == -10 && currentPlayer.getCarrots() > 30) {
					// only remove carrots if at end
					return new RatedMove(move, 1);
				}
			} else if (action instanceof FallBack) {
				if (currentPlayer.getCarrots() < 10 && currentPlayer.getFieldIndex()
						- gameState.getPreviousFieldByType(FieldType.HEDGEHOG, currentPlayer.getFieldIndex()) < 5) {
					// go back scarcly
					return new RatedMove(move, 1);
				}
			}
		}
		return new RatedMove(move, Integer.MIN_VALUE);
	}

	public static Move getSimpleMove(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		ArrayList<RatedMove> ratedMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			ratedMoves.add(LogicHelper.getRatedMove(move, gameState, currentPlayer));
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

		return selectedMove.getMove();
	}

	public static Move getSimpleEndMove(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		ArrayList<RatedMove> ratedMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			ratedMoves.add(LogicHelper.getEndRatedMove(move, gameState, currentPlayer));
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

		return selectedMove.getMove();
	}

	public static Move getNextAdvance(ArrayList<Move> possibleMoves) {
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
			return selectedMove;
		}
		return null;
	}

	public static Move getHareEatSalad(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
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
		int nextHareUnoccupied = LogicHelper.getNextUnoccupied(FieldType.HARE, currentPlayer.getFieldIndex(), gameState,
				currentPlayer);
		for (Move move : saladMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					// check if this field is the nearest
					if (advance.getDistance() + currentPlayer.getFieldIndex() == nextHareUnoccupied) {
						return move;
					}
				}
			}
		}

		return null;
	}

	public static Move getNextByType(FieldType fieldType, ArrayList<Move> possibleMoves, GameState gameState,
			Player currentPlayer) {
		int nextFieldOfType = LogicHelper.getNextUnoccupied(fieldType, currentPlayer.getFieldIndex(), gameState,
				currentPlayer);
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					if (advance.getDistance() + currentPlayer.getFieldIndex() == nextFieldOfType) {
						return move;
					}
				}
			}
		}

		return null;
	}

	public static int getMoveRating(Move move, GameState gamestate, int depth) {
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
			return -depth;
		} catch (InvalidGameStateException e) {
			// wtf, let's just ignore this but better not perform this move
			return -depth;
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
						return -depth;
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

	public static Move getWinningMove(ArrayList<Move> possibleMoves, int depth, GameState gameState, long startTime) {
		Move selectedMove = null;
		int highestRating = 0;
		for (Move move : possibleMoves) {
			if(!timeEnough(startTime)) {
				break;
			}
			boolean calculate = true;
			for(Action action:move.getActions()) {
				if(action instanceof Card) {
					calculate = false;
				}
			}
			if(!calculate) {
				break;
			}
			int rating = getMoveRating(move, gameState, depth);
			if (rating > highestRating) {
				selectedMove = move;
				highestRating = rating;
			}
		}
		if(highestRating>0) {
			return selectedMove;
		}
		return null;
	}
}
