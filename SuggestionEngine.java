import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.*;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SuggestionEngine extends Java8BaseListener {
	class Candidate implements Comparable<Candidate> {
		String methodName;
		double distance;
		public Candidate(String methodName, double distance) {
			this.methodName = methodName;
			this.distance = distance;
		}

		public int compareTo(Candidate c) {
			int difference = (int) (this.distance - c.distance);
			if (difference == 0 && !methodName.equals(c.methodName)) {
				return 1;
			}
			return difference;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Candidate) {
				return ((Candidate)o).methodName.equals(
					this.methodName);
			}
			return super.equals(o);
		}

		@Override
		public int hashCode() {
			return methodName.hashCode();
		}
	}

	private final static Logger LOGGER = Logger.getLogger(SuggestionEngine.class.getName());

	public static void main(String[] args) throws IOException {
		LOGGER.setLevel(Level.INFO);
		LOGGER.info("Info Log");
		SuggestionEngine se = new SuggestionEngine();
		InputStream code = System.in;
		String word = args[0];
		int topK = Integer.valueOf(args[1]);
		TreeSet<Candidate> suggestions = se.suggest(code, word, topK);

		System.out.println("Suggestions are found:");
		while (!suggestions.isEmpty()) {
			Candidate c = suggestions.pollFirst();
			System.out.println(c.methodName + ": " + c.distance);
		}
	}

	List<String> mMethods;

	public TreeSet<Candidate> suggest(InputStream code,
					  String word,
					  int topK)
	throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(System.in);
		Java8Lexer lexer = new Java8Lexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		Java8Parser parser = new Java8Parser(tokens);
		LOGGER.info("Building the parse tree...");
		long start = System.nanoTime();
		ParseTree tree = parser.compilationUnit();
		long elapsedNano = System.nanoTime() - start;
		long elapsedSec =
			TimeUnit.SECONDS.convert(elapsedNano, TimeUnit.NANOSECONDS);
		LOGGER.info(String.format(
			"Built the parse tree...(took %d seconds)", elapsedSec));
		ParseTreeWalker walker = new ParseTreeWalker();
		mMethods = new ArrayList<>();

		LOGGER.info("Collecting the public method names...");
		walker.walk(this, tree);
		LOGGER.info("Collected the public method names...");
		LOGGER.info(mMethods.toString());

		LOGGER.info("Finding the suggestions...");
		return getTopKNeighbor(word, topK);
	}

	@Override
	public void enterMethodDeclaration(
		Java8Parser.MethodDeclarationContext ctx) {
			String methodName=null;
			if(ctx.methodModifier(0)!=null){	// some methods don't have method modifier which means they have default modifier.
				if((ctx.methodModifier(0).getText()).equals("public")){	// First argument of method modifier can contain public key.
					methodName = ctx.methodHeader().methodDeclarator().Identifier()+"";	// Adds method name to mMethods list.
				}	
			}

			// Methods can be overloaded or recursive,following line makes sure that mMethod list doesn't have recurring method names.
			if(!(mMethods.contains(methodName)) && methodName!=null)
				mMethods.add(methodName);

	}

	private TreeSet<Candidate> getTopKNeighbor(String word, int K) {
		TreeSet<Candidate> minHeap = new TreeSet<>();

		List<Candidate> candidates=new ArrayList<>();

		for(int i=0;i<mMethods.size();i++){		// calculates distance of methods, creates a candidate object with the specified method name and distance and adds it to candidates list.
			double distance = Levenshtein.distance(word, mMethods.get(i));
			candidates.add(new Candidate(mMethods.get(i), distance));
		}

		String tempName;
		double tempDis;
		for(int i=0;i<candidates.size();i++){	// sort the candidates list from the least distance to the most.
			for(int j=i+1;j<candidates.size();j++){
				if(candidates.get(i).distance>candidates.get(j).distance){
					tempName=candidates.get(i).methodName;
					tempDis=candidates.get(i).distance;
					candidates.get(i).methodName=candidates.get(j).methodName;
					candidates.get(i).distance=candidates.get(j).distance;
					candidates.get(j).methodName=tempName;
					candidates.get(j).distance=tempDis;

				}
			}
		}

		for(int i=0;i<candidates.size();i++){	// adds K methods with the least distance to the minHeap.
			if(i==K)
				break;
			minHeap.add(candidates.get(i));
		}


		return minHeap;
	}
}
