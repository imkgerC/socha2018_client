package sc.player2018.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sc.plugin2018.Action;
import sc.plugin2018.Advance;
import sc.plugin2018.Card;
import sc.plugin2018.ExchangeCarrots;
import sc.plugin2018.FallBack;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;
import sc.plugin2018.Player;
import sc.plugin2018.util.Constants;
import sc.plugin2018.util.GameRuleLogic;

public class EndGameLogic {
	public static Move getTurn(GameState gameState) {
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();
		Player currentPlayer = gameState.getCurrentPlayer();
		int currentIndex = currentPlayer.getFieldIndex();
		int carrots = currentPlayer.getCarrots();
		MoveList baseList = new MoveList(possibleMoves, gameState);

		if (currentIndex < 47) {
			Move returnMove = getFurthestPosMove(possibleMoves, gameState, currentIndex);
			if (returnMove != null) {
				return returnMove;
			}
			returnMove = getFarAdvance(possibleMoves);
			if (returnMove != null) {
				return returnMove;
			}
		} else {
			if (gameState.getTypeAt(currentIndex) != FieldType.CARROT) {
				Move returnMove = baseList.select(FieldType.CARROT).getFurthest();
				if (returnMove != null) {
					return returnMove;
				}
			} else {
				int fieldsFromGoal = (Constants.NUM_FIELDS - 1) - currentIndex;
				int carrotsNeeded = GameRuleLogic.calculateCarrots(fieldsFromGoal);
				// we can't go into the goal or we would have already, let's figure out why
				if (carrots >= carrotsNeeded) {
					// we have enough carrots for moving to the goal, so we are having too many
					// carrots
					Move returnMove = baseList.getCarrotExchange(-10);
					if (returnMove != null) {
						return returnMove;
					}
				} else {
					// we are short on carrots
					// we should calculate if moving or waiting is a better option
					int turnsBySitting = (int) (Math.ceil((double) (carrotsNeeded - carrots) / 10.0));
					int turnsByMoving = LogicHelper.minimumNumberOfTurns(fieldsFromGoal, carrots);
					if (turnsByMoving < turnsBySitting) {
						// we should move
						int bestDistance = (int) Math.floor(fieldsFromGoal / turnsByMoving);
						Move returnMove = baseList.select(FieldType.CARROT).getNearestTo(bestDistance);
						if (returnMove != null) {
							return returnMove;
						}
					} else {
						// we should sit
						Move returnMove = baseList.getCarrotExchange(10);
						if (returnMove != null) {
							return returnMove;
						}
					}
				}
			}

			return getSimpleEndMove(possibleMoves, gameState, currentPlayer);
		}

		return null;
	}

	private static Move getFurthestPosMove(ArrayList<Move> possibleMoves, GameState gameState, int currentIndex) {
		List<Move> advanceMoves = new ArrayList<>();
		int currentEnemyPos = gameState.getOtherPlayer().getFieldIndex();
		int furthestEnemyPos = GameRuleLogic.calculateMoveableFields(gameState.getOtherPlayer().getCarrots())
				+ currentEnemyPos;

		for (Move move : possibleMoves) {
			Advance advance = LogicHelper.getAdvance(move);
			if (advance != null) {
				int destination = currentIndex + advance.getDistance();
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
			Collections.sort(advanceMoves, LogicHelper.highestDistanceComparator);
			return advanceMoves.get(0);
		}

		return null;
	}

	private static Move getFarAdvance(ArrayList<Move> possibleMoves) {
		// taking the third most far away field should be okay for now
		List<Move> consideredMoves = new ArrayList<>();
		for (Move move : possibleMoves) {
			boolean hasCard = false;
			for (Action action : move.actions) {
				if (action instanceof Card) {
					hasCard = true;
				}
			}
			if (!hasCard) {
				if (LogicHelper.getAdvance(move) != null) {
					consideredMoves.add(move);
				}
			}
		}

		if (consideredMoves.size() > 0) {
			Collections.sort(consideredMoves, LogicHelper.highestDistanceComparator);
			return consideredMoves.get(2);
		}

		return null;
	}

	private static int getEndMoveRating(Move move, GameState gameState, Player currentPlayer) {
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

	private static Move getSimpleEndMove(ArrayList<Move> possibleMoves, GameState gameState, Player currentPlayer) {
		Move selectedMove = new Move();
		int highestRating = -1;
		for (Move move : possibleMoves) {
			if (getEndMoveRating(move, gameState, currentPlayer) > highestRating) {
				selectedMove = move;
			}
		}
		return selectedMove;
	}

}
