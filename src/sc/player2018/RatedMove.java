package sc.player2018;

public class RatedMove extends sc.plugin2018.Move {
	int rating;
	
	public RatedMove(){
		this.rating = Integer.MIN_VALUE;
	}
	
	public RatedMove(sc.plugin2018.Move move){
		this.actions = move.actions;
		this.rating = Integer.MIN_VALUE;
	}
	
	public RatedMove(sc.plugin2018.Move move, int rating){
		this.rating = rating;
		this.actions = move.actions;
	}
	
	public sc.plugin2018.Move getMove(){
		return new sc.plugin2018.Move(this.actions);
	}
	
	public void setRating(int rating) {
		this.rating = rating;
	}
	
	public void setMove(sc.plugin2018.Move move) {
		this.actions = move.actions;
	}
	
	public int getRating() {
		return this.rating;
	}
	
	public RatedMove copy() {
		return new RatedMove(this.getMove(),this.rating);
	}
}
