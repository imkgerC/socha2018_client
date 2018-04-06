package sc.player2018.logic;

import java.util.ArrayList;
import java.util.Comparator;
import org.slf4j.Logger;
import sc.plugin2018.*;
import sc.plugin2018.util.Constants;

public class LogicHelper {

	private static final int ms_to_nano_factor = 1000000;

	public static Comparator<Move> lowestDistanceComparator = new Comparator<Move>() {
		@Override
		public int compare(Move m1, Move m2) {
			Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
			return ((Integer) a1.getDistance()).compareTo(a2.getDistance());
		}
	};
	public static Comparator<Move> highestDistanceComparator = new Comparator<Move>() {
		@Override
		public int compare(Move m1, Move m2) {
			Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
			return ((Integer) a1.getDistance()).compareTo(a2.getDistance()) * -1;
		}
	};

	public static void prepareEnd(long startTime, Logger log) {
		// simple info message
		long nowTime = System.nanoTime();
		log.warn("Time needed for turn:" + (nowTime - startTime) / ms_to_nano_factor); // printed in warn, as info
																						// mostly isn't shown
		return;
	}
	
	public static int getMoveRating(Move move, GameState gameState, Player currentPlayer) {
		// method is used if nothing else could be found or an emergency emerges
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				Advance advance = (Advance) action;
				if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
					// winning move
					return Integer.MAX_VALUE;

				} else if (gameState.getBoard()
						.getTypeAt(advance.getDistance() + currentPlayer.getFieldIndex()) == FieldType.SALAD) {
					// advance to a salad field
					return 4;
				} else {
					// complicated formula for calculating some semi-random bullcrap
					int carrotsNeeded = (int) ((advance.getDistance() + 1) * ((float) advance.getDistance() / 2));
					int awayFromGoalAfter = Constants.NUM_FIELDS
							- (currentPlayer.getFieldIndex() + advance.getDistance() + 1);
					int carrotsNeededToGoal = (int) ((awayFromGoalAfter + 1) * ((float) awayFromGoalAfter / 2))
							+ carrotsNeeded;
					return 10 - (currentPlayer.getCarrots() - carrotsNeededToGoal);
				}
			} else if (action instanceof Card) {
				Card card = (Card) action;
				if (card.getType() == CardType.EAT_SALAD) {
					return 3;
				}
			} else if (action instanceof ExchangeCarrots) {
				ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
				if (exchangeCarrots.getValue() == 10 && currentPlayer.getCarrots() < 30
						&& currentPlayer.getFieldIndex() < 56
						&& !(currentPlayer.getLastNonSkipAction() instanceof ExchangeCarrots)) {
					// do not take carrots in the end game
					return Integer.MIN_VALUE;
				} else if (exchangeCarrots.getValue() == -10 && currentPlayer.getCarrots() > 30
						&& currentPlayer.getFieldIndex() >= 40) {
					// only remove carrots if at end
					return 1;
				}
			} else if (action instanceof FallBack) {
				if (currentPlayer.getFieldIndex() > 56 /* last salad-field */ && currentPlayer.getSalads() > 0) {
					// fall back if you are at the end and have not eaten all the salads
					return 3;
				} else if (currentPlayer.getFieldIndex() <= 56 && currentPlayer.getFieldIndex()
						- gameState.getPreviousFieldByType(FieldType.HEDGEHOG, currentPlayer.getFieldIndex()) < 5) {
					// never go back in end game
					return Integer.MIN_VALUE;
				}
			} else if (action instanceof EatSalad) {
				// Eat salads you dumb shit
				return 4;
			}
		}
		return Integer.MIN_VALUE;
	}

	public static Move getSimpleMove(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		Move selectedMove = new Move();
		int highestRating = -1;
		for (Move move : possibleMoves) {
			if (getMoveRating(move, gameState, currentPlayer) > highestRating) {
				selectedMove = move;
			}
		}
		return selectedMove;
	}

	public static Advance getAdvance(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				return (Advance) action;
			}
		}
		return null;
	}
	
	public static Card getCard(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Card) {
				return (Card) action;
			}
		}
		return null;
	}
	
	public static int minimumNumberOfTurns(int fieldsAway, int carrots) {
		int turns = 1;
		for (int i = 0; i < 100; i++) {
			int needed = carrotsForReaching(fieldsAway, turns);
			if (needed > carrots) {
				turns++;
			} else {
				return turns;
			}
		}
		return Integer.MAX_VALUE;
	}

	public static int carrotsForReaching(int fieldsAway, int turns) {
		double y = turns;
		double n = fieldsAway;
		double result = (y / 2) + ((Math.pow(y, 2.0)) / (2 * n));

		return (int) result;
	}
}
