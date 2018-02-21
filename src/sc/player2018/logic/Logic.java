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

	public RatedMove getRatedMove(Move move) {
		for (Action action : move.actions) {
			if (action instanceof Advance) {
				Advance advance = (Advance) action;
				if (advance.getDistance() + currentPlayer.getFieldIndex() == Constants.NUM_FIELDS - 1) {
					// Zug ins Ziel
					return new RatedMove(move, Integer.MAX_VALUE);

				} else if (gameState.getBoard()
						.getTypeAt(advance.getDistance() + currentPlayer.getFieldIndex()) == FieldType.SALAD) {
					// Zug auf Salatfeld
					return new RatedMove(move, 4);
				} else {
					// Ziehe Vorwärts, wenn möglich
					return new RatedMove(move, 0);
				}
			} else if (action instanceof Card) {
				Card card = (Card) action;
				if (card.getType() == CardType.EAT_SALAD) {
					// Zug auf Hasenfeld und danch Salatkarte
					return new RatedMove(move, 4);
				} // Muss nicht zusätzlich ausgewählt werden, wurde schon durch Advance
					// ausgewählt
			} else if (action instanceof ExchangeCarrots) {
				ExchangeCarrots exchangeCarrots = (ExchangeCarrots) action;
				if (exchangeCarrots.getValue() == 10 && currentPlayer.getCarrots() < 30
						&& currentPlayer.getFieldIndex() < 40
						&& !(currentPlayer.getLastNonSkipAction() instanceof ExchangeCarrots)) {
					// Nehme nur Karotten auf, wenn weniger als 30 und nur am Anfang und nicht zwei
					// mal hintereinander
					return new RatedMove(move, 0);
				} else if (exchangeCarrots.getValue() == -10 && currentPlayer.getCarrots() > 30
						&& currentPlayer.getFieldIndex() >= 40) {
					// abgeben von Karotten ist nur am Ende sinnvoll
					return new RatedMove(move, 0);
				}
			} else if (action instanceof FallBack) {
				if (currentPlayer.getFieldIndex() > 56 /* letztes Salatfeld */ && currentPlayer.getSalads() > 0) {
					// Falle nur am Ende (currentPlayer.getFieldIndex() > 56) zurück, außer du
					// musst noch einen Salat
					// loswerden
					return new RatedMove(move, 3);
				} else if (currentPlayer.getFieldIndex() <= 56 && currentPlayer.getFieldIndex()
						- gameState.getPreviousFieldByType(FieldType.HEDGEHOG, currentPlayer.getFieldIndex()) < 5) {
					// Falle zurück, falls sich Rückzug lohnt (nicht zu viele Karotten aufnehmen)
					return new RatedMove(move, -1);
				}
			} else {
				// FÃüe Salatessen oder Skip hinzu
				return new RatedMove(move, -1);
			}
		}
		return new RatedMove(move,Integer.MIN_VALUE);
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
		long nowTime = System.nanoTime();
		sendAction(selectedMove.getMove());
		log.warn("Time needed for turn: {}", (nowTime - startTime) / 1000000);
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
		client.sendMove(move);
	}

}
