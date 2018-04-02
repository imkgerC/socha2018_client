package sc.player2018.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import sc.plugin2018.Action;
import sc.plugin2018.Advance;
import sc.plugin2018.Card;
import sc.plugin2018.CardType;
import sc.plugin2018.EatSalad;
import sc.plugin2018.ExchangeCarrots;
import sc.plugin2018.FallBack;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;

public class MoveList {

	public List<Move> moves = new ArrayList<Move>();
	public GameState gameState;
	private int currentIndex = -1;

	public MoveList(List<Move> moves, GameState gameState) {
		this.moves = moves;
		this.gameState = gameState;
		this.currentIndex = gameState.getCurrentPlayer().getFieldIndex();
	}

	public static MoveList add(MoveList m1, MoveList m2) {
		List<Move> merge = new ArrayList<Move>(m1.moves);
		merge.addAll(m2.moves);
		merge = merge.stream().distinct().collect(Collectors.toList());
		return new MoveList(merge, m1.gameState);
	}

	public MoveList deselect(FieldType fieldType) {
		List<Move> selectedMoves = new ArrayList<Move>();
		for (Move move : this.moves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				int destination = this.currentIndex + advance.getDistance();
				if (this.gameState.getTypeAt(destination) != fieldType) {
					selectedMoves.add(move);
				}
			} else {
				selectedMoves.add(move);
			}
		}
		return new MoveList(selectedMoves, this.gameState);
	}

	public MoveList select(FieldType fieldType) {
		List<Move> selectedMoves = new ArrayList<Move>();
		for (Move move : this.moves) {
			Advance advance = getAdvance(move);
			if (advance != null) {
				int destination = this.currentIndex + advance.getDistance();
				if (this.gameState.getTypeAt(destination) == fieldType) {
					selectedMoves.add(move);
				}
			}
		}
		return new MoveList(selectedMoves, this.gameState);
	}

	public MoveList deselect(CardType cardType) {
		List<Move> selectedMoves = new ArrayList<Move>();
		for (Move move : this.moves) {
			Card card = getCard(move);
			if (card != null) {
				if (card.getType() != cardType) {
					selectedMoves.add(move);
				}
			} else {
				selectedMoves.add(move);
			}
		}
		return new MoveList(selectedMoves, this.gameState);
	}

	public MoveList select(CardType cardType) {
		List<Move> selectedMoves = new ArrayList<Move>();
		for (Move move : this.moves) {
			Card card = getCard(move);
			if (card != null) {
				if (card.getType() == cardType) {
					selectedMoves.add(move);
				}
			}
		}
		return new MoveList(selectedMoves, this.gameState);
	}

	public MoveList deselect(CardType cardType, int value) {
		List<Move> selectedMoves = new ArrayList<Move>();
		for (Move move : this.moves) {
			Card card = getCard(move);
			if (card != null) {
				if (card.getType() != cardType || card.getValue() != value) {
					selectedMoves.add(move);
				}
			} else {
				selectedMoves.add(move);
			}
		}
		return new MoveList(selectedMoves, this.gameState);
	}

	public MoveList select(CardType cardType, int value) {
		List<Move> selectedMoves = new ArrayList<Move>();
		for (Move move : this.moves) {
			Card card = getCard(move);
			if (card != null) {
				if (card.getType() == cardType && card.getValue() == value) {
					selectedMoves.add(move);
				}
			}
		}
		return new MoveList(selectedMoves, this.gameState);
	}

	public Move getFurthest(int index) {
		if (this.moves.size() > index) {
			Collections.sort(this.moves, getFurthestComparator());
			return this.moves.get(index);
		}
		return null;
	}

	public Move getFurthest() {
		return this.getFurthest(0);
	}

	public Move getNearest(int index) {
		if (this.moves.size() > index) {
			Collections.sort(this.moves, getNearestComparator());
			return this.moves.get(index);
		}
		return null;
	}

	public Move getNearest() {
		return this.getNearest(0);
	}

	public Move getNearestTo(int wantedDistance, int index) {
		if (this.moves.size() > index) {
			Collections.sort(this.moves, getNearestToComparator(wantedDistance));
			return this.moves.get(index);
		}
		return null;
	}

	public Move getNearestTo(int wantedDistance) {
		return this.getNearestTo(wantedDistance, 0);
	}

	public Move getCarrotExchange(int value) {
		for (Move move : this.moves) {
			for (Action action : move.actions) {
				if (action instanceof ExchangeCarrots) {
					ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
					if (exchangeCarrots.getValue() == value) {
						return move;
					}
				}
			}
		}
		return null;
	}

	public Move getSaladEat() {
		for (Move move : this.moves) {
			for (Action action : move.actions) {
				if (action instanceof EatSalad) {
					return move;
				}
			}
		}
		return null;
	}

	public Move getFallback() {
		for (Move move : this.moves) {
			for (Action action : move.actions) {
				if (action instanceof FallBack) {
					return move;
				}
			}
		}
		return null;
	}

	private static Card getCard(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Card) {
				return (Card) action;
			}
		}
		return null;
	}

	private static Advance getAdvance(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				return (Advance) action;
			}
		}
		return null;
	}

	private static Comparator<Move> nearestComparator = new Comparator<Move>() {
		@Override
		public int compare(Move m1, Move m2) {
			Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
			int distance1 = Integer.MAX_VALUE, distance2 = Integer.MAX_VALUE;
			if (a1 != null) {
				distance1 = a1.getDistance();
			}
			if (a2 != null) {
				distance2 = a2.getDistance();
			}
			return ((Integer) distance1).compareTo(distance2);
		}
	};
	private static Comparator<Move> furthestComparator = new Comparator<Move>() {
		@Override
		public int compare(Move m1, Move m2) {
			Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
			int distance1 = Integer.MIN_VALUE, distance2 = Integer.MIN_VALUE;
			if (a1 != null) {
				distance1 = a1.getDistance();
			}
			if (a2 != null) {
				distance2 = a2.getDistance();
			}
			return ((Integer) distance1).compareTo(distance2) * -1;
		}
	};

	private static Comparator<Move> getNearestComparator() {
		return nearestComparator;
	}

	private static Comparator<Move> getFurthestComparator() {
		return furthestComparator;
	}

	private static Comparator<Move> getNearestToComparator(int wantedDistance) {
		return new Comparator<Move>() {
			@Override
			public int compare(Move m1, Move m2) {
				Advance a1 = getAdvance(m1), a2 = getAdvance(m2);
				int distanceTo1 = Integer.MAX_VALUE, distanceTo2 = Integer.MAX_VALUE;
				if (a1 != null) {
					distanceTo1 = Math.abs(a1.getDistance() - wantedDistance);
				}
				if (a1 != null) {
					distanceTo2 = Math.abs(a2.getDistance() - wantedDistance);
				}
				return ((Integer) distanceTo1).compareTo(distanceTo2);
			}
		};
	}
}
