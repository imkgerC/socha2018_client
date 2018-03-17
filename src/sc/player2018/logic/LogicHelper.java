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
import sc.plugin2018.util.GameRuleLogic;

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

	public static int getEndMoveRating(Move move, GameState gameState, Player currentPlayer) {
		// method is used if nothing else could be found or an emergency emerges
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				Advance advance = (Advance) action;
				if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
					// winning move
					return Integer.MAX_VALUE;

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
							return 10;
						}
					}
					return advance.getDistance() - 2;
				}
			} else if (action instanceof ExchangeCarrots) {
				ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
				if (((currentPlayer.getCarrots() < 30 && currentPlayer.getFieldIndex() < 56)
						|| (currentPlayer.getFieldIndex() > 56 && currentPlayer.getCarrots() <= 10))
						&& exchangeCarrots.getValue() == 10) {
					// do not take carrots in end game
					return 2;
				} else if (exchangeCarrots.getValue() == -10 && currentPlayer.getCarrots() > 30) {
					// only remove carrots if at end
					return 1;
				}
			} else if (action instanceof FallBack) {
				if (currentPlayer.getCarrots() < 10 && currentPlayer.getFieldIndex()
						- gameState.getPreviousFieldByType(FieldType.HEDGEHOG, currentPlayer.getFieldIndex()) < 5) {
					// go back scarcely
					return -1;
				}
			}
		}
		return Integer.MIN_VALUE;
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
		Move selectedMove = new Move();
		int highestRating = -1;
		for (Move move : possibleMoves) {
			if (LogicHelper.getEndMoveRating(move, gameState, currentPlayer) > highestRating) {
				selectedMove = move;
			}
		}
		return selectedMove;
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

	public static Move getLastAdvance(ArrayList<Move> possibleMoves) {
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
					if (advance.getDistance() > selectedAdvance.getDistance()) {
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

	public static Move getAdvanceFarAway(ArrayList<Move> possibleMoves) {
		// taking the third most far away field should be okay for now
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
		return selectedMoves[2];
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

	public static Move getNextHareCarrot(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		List<Move> hareMoves = new ArrayList<>();

		// select all moves that play out a salad card
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Card) {
					Card card = (Card) action;
					if (card.getType() == CardType.TAKE_OR_DROP_CARROTS) {
						if (card.getValue() == 20) {
							hareMoves.add(move);
						}

					}
				}
			}
		}
		// select the nearest hare field
		int nextHareUnoccupied = LogicHelper.getNextUnoccupied(FieldType.HARE, currentPlayer.getFieldIndex(), gameState,
				currentPlayer);
		for (Move move : hareMoves) {
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

	public static Move getLastByType(FieldType fieldType, ArrayList<Move> possibleMoves, GameState gameState,
			Player currentPlayer) {
		Move selectedMove = null;
		int furthestDistance = -1;
		for (Move move : possibleMoves) {
			for (Action action : move.actions) {
				if (action instanceof Advance) {
					Advance advance = (Advance) action;
					int destination = advance.getDistance() + currentPlayer.getFieldIndex();
					if (gameState.getTypeAt(destination) == fieldType) {
						if (destination > furthestDistance) {
							furthestDistance = destination;
							selectedMove = move;
						}
					}
				}
			}
		}

		return selectedMove;
	}

	private static Advance getAdvance(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				return (Advance) action;
			}
		}
		return null;
	}

	public static Move getFurthestPos(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		List<Move> advanceMoves = new ArrayList<>();
		int currentEnemyPos = gameState.getOtherPlayer().getFieldIndex();
		int furthestEnemyPos = GameRuleLogic.calculateMoveableFields(gameState.getOtherPlayer().getCarrots())
				+ currentEnemyPos;

		for (Move move : possibleMoves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				int destination = currentPlayer.getFieldIndex() + advance.getDistance();
				if (gameState.getTypeAt(destination) == FieldType.POSITION_1) {
					if (destination >= furthestEnemyPos) {
						advanceMoves.add(move);
					}
				} else if (gameState.getTypeAt(destination) == FieldType.POSITION_2) {
					if (destination < currentEnemyPos) {
						advanceMoves.add(move);
					}
				}
			}
		}

		if (advanceMoves.size() > 0) {
			Collections.sort(advanceMoves, new Comparator<Move>() {
				@Override
				public int compare(Move m1, Move m2) {
					Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
					return ((Integer) a1.getDistance()).compareTo(a2.getDistance()) * -1;
				}
			});

			return advanceMoves.get(0);
		}

		return null;
	}

	public static Move getEndStrategyMove(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		int currentPos = currentPlayer.getFieldIndex();
		if (gameState.getTypeAt(currentPos) != FieldType.CARROT) {
			return LogicHelper.getLastByType(FieldType.CARROT, possibleMoves, gameState, currentPlayer);
		}
		// we can't go into the goal or we would have already, let's figure out why
		if (GameRuleLogic.calculateMoveableFields(currentPlayer.getCarrots()) >= (Constants.NUM_FIELDS - 1)
				- currentPos) {
			// we have enough carrots for moving to the desired field, so it must be that we
			// have too many carrots
			for (Move move : possibleMoves) {
				for (Action action : move.actions) {
					if (action instanceof ExchangeCarrots) {
						ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
						if (exchangeCarrots.getValue() == -10) {
							return move;
						}
					}
				}
			}
		} else {
			// we are short on carrots
			int fieldsFromGoal = Constants.NUM_FIELDS - 1 - currentPlayer.getFieldIndex();
			int carrotsNeeded = GameRuleLogic.calculateCarrots(fieldsFromGoal);
			int turnsOfSitting = (int) Math.ceil(carrotsNeeded / 10);
			int turnsByMoving = minimumNumberOfTurns(fieldsFromGoal, currentPlayer.getCarrots());
			if (turnsByMoving > turnsOfSitting) {
				for (Move move : possibleMoves) {
					for (Action action : move.actions) {
						if (action instanceof ExchangeCarrots) {
							ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
							if (exchangeCarrots.getValue() == +10) {
								return move;
							}
						}
					}
				}
			} else {
				int bestDistance = (int) Math.floor(fieldsFromGoal / turnsByMoving);
				List<Move> advanceMoves = new ArrayList<>();
				for (Move move : possibleMoves) {
					Advance advance = getAdvance(move);
					if (advance != null) {
						int destination = advance.getDistance() + currentPlayer.getFieldIndex();
						if (gameState.getTypeAt(destination) == FieldType.CARROT) {
							advanceMoves.add(move);
						}
					}
				}

				if (advanceMoves.size() > 0) {
					Collections.sort(advanceMoves, new Comparator<Move>() {
						@Override
						public int compare(Move m1, Move m2) {
							Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
							int fromPerfect1 = Math.abs(a1.getDistance() - bestDistance);
							int fromPerfect2 = Math.abs(a2.getDistance() - bestDistance);
							return ((Integer) fromPerfect1).compareTo(fromPerfect2) * -1;
						}
					});

					return advanceMoves.get(0);
				}
			}
		}
		return null;
	}

	private static int minimumNumberOfTurns(int fieldsAway, int carrots) {
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

	private static int carrotsForReaching(int fieldsAway, int turns) {
		double y = turns;
		double n = fieldsAway;
		double result = (y / 2) + ((Math.pow(y, 2.0)) / (2 * n));

		return (int) result;
	}
}
