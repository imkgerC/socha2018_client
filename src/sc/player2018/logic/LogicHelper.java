package sc.player2018.logic;

import java.security.SecureRandom;
import sc.player2018.RatedMove;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import sc.plugin2018.*;
import sc.plugin2018.util.Constants;

public class LogicHelper {

	private static final int ms_to_nano_factor = 1000000;
	private static final Random rand = new SecureRandom();

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

	private static int getNextUnoccupied(FieldType fieldType, int index, int depth, GameState gameState) {
		// recursive function as fields can be occupied or the gameState can bug around
		int temp = gameState.getNextFieldByType(fieldType, index);
		if (temp < 0 || depth >= 20) {
			// preventing stack overflow
			return -1;
		}
		if (gameState.isOccupied(temp)) {
			// return next field after this one
			return getNextUnoccupied(fieldType, temp, depth + 1, gameState);
		}
		return temp;
	}

	public static int getNextUnoccupied(FieldType fieldType, int index, GameState gameState) {
		return getNextUnoccupied(fieldType, index, 0, gameState);
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

	public static Move getNextAdvance(ArrayList<Move> possibleMoves) {
		List<Move> advanceMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				advanceMoves.add(move);
			}
		}

		if (advanceMoves.size() > 0) {
			Collections.sort(advanceMoves, LogicHelper.lowestDistanceComparator);
			return advanceMoves.get(0);
		}
		return null;
	}

	public static Move getLastAdvance(ArrayList<Move> possibleMoves) {
		List<Move> advanceMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				advanceMoves.add(move);
			}
		}

		if (advanceMoves.size() > 0) {
			Collections.sort(advanceMoves, LogicHelper.highestDistanceComparator);
			return advanceMoves.get(0);
		}
		return null;
	}

	public static Move getHareEatSalad(ArrayList<Move> possibleMoves, GameState gameState, int currentIndex) {
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
		if (saladMoves.size() > 0) {
			Collections.sort(saladMoves, lowestDistanceComparator);
			return saladMoves.get(0);
		}

		return null;
	}

	public static Move getNextHareCarrot(ArrayList<Move> possibleMoves, GameState gameState, int currentIndex) {
		ArrayList<Move> hareMoves = new ArrayList<>();

		// select all moves that play out a carrot card
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Card) {
					Card card = (Card) action;
					if (card.getType() == CardType.TAKE_OR_DROP_CARROTS) {
						if(card.getValue() == 20) {
							hareMoves.add(move);
						}
					}
				}
			}
		}
		if (hareMoves.size() > 0) {
			Collections.sort(hareMoves, lowestDistanceComparator);
			return hareMoves.get(0);
		}

		return null;
	}

	public static Move getNextByType(FieldType fieldType, ArrayList<Move> possibleMoves, GameState gameState,
			int currentIndex) {
		List<Move> consideredMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				int destination = currentIndex + advance.getDistance();
				if (gameState.getTypeAt(destination) == fieldType) {
					consideredMoves.add(move);
				}
			}
		}

		if (consideredMoves.size() > 0) {
			Collections.sort(consideredMoves, lowestDistanceComparator);
			return consideredMoves.get(0);
		}

		return null;
	}

	public static Move getLastByType(FieldType fieldType, ArrayList<Move> possibleMoves, GameState gameState,
			int currentIndex) {
		List<Move> consideredMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				int destination = currentIndex + advance.getDistance();
				if (gameState.getTypeAt(destination) == fieldType) {
					consideredMoves.add(move);
				}
			}
		}

		if (consideredMoves.size() > 0) {
			Collections.sort(consideredMoves, highestDistanceComparator);
			return consideredMoves.get(0);
		}

		return null;
	}

	public static Advance getAdvance(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				return (Advance) action;
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
