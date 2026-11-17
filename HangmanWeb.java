import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Hangman Web - DSA Lab Project (runs on localhost:9090)
 *
 * Data structures used (all in Java, on the server):
 *  - Array   : word bank
 *  - Queue   : upcoming words
 *  - HashSet : guessed letters (O(1) duplicate check)
 *  - Stack   : guess history (undo)
 *
 * Run:  javac HangmanWeb.java
 *       java HangmanWeb
 * Open: http://localhost:9090   (index.html must be in the same folder)
 */
public class HangmanWeb {

    static final String[] WORDS = {
        "java", "stack", "queue", "array", "pointer", "recursion",
        "algorithm", "hashing", "linkedlist", "binary", "compiler", "variable"
    };
    static final int MAX_LIVES = 6;

    static Queue<String> wordQueue;
    static String word;
    static Set<Character> guessed;
    static Stack<Character> history;
    static int lives, score, played;
    static String message = "";

    // ---------- game logic ----------

    static synchronized void newGame() {
        List<String> list = new ArrayList<>(Arrays.asList(WORDS));
        Collections.shuffle(list);
        wordQueue = new LinkedList<>(list);
        score = 0;
        played = 0;
        nextWord();
    }

    static void nextWord() {
        word = wordQueue.poll();               // dequeue
        guessed = new HashSet<>();
        history = new Stack<>();
        lives = MAX_LIVES;
        message = (word == null) ? "All words done!" : "New word. Good luck!";
    }

    static boolean solved() {
        for (char ch : word.toCharArray()) {
            if (!guessed.contains(ch)) return false;
        }
        return true;
    }

    static String status() {
        if (word == null) return "finished";
        if (solved()) return "won";
        if (lives <= 0) return "lost";
        return "playing";
    }

    static synchronized void guess(char c) {
        if (!status().equals("playing")) return;
        c = Character.toLowerCase(c);
        if (!Character.isLetter(c)) { message = "Letters only."; return; }

        if (!guessed.add(c)) {                 // HashSet duplicate check
            message = "Already guessed '" + c + "'.";
            return;
        }
        history.push(c);                       // Stack push

        if (word.indexOf(c) >= 0) {
            message = "Correct!";
        } else {
            lives--;
            message = "Wrong!";
        }

        if (solved()) {
            score++; played++;
            message = "You won!";
        } else if (lives <= 0) {
            played++;
            message = "You lost!";
        }
    }

    static synchronized void undo() {
        if (!status().equals("playing")) { message = "Round is over."; return; }
        if (history.isEmpty()) { message = "Nothing to undo."; return; }
        char last = history.pop();             // Stack pop
        guessed.remove(last);
        if (word.indexOf(last) < 0) lives++;   // give life back
        message = "Undid '" + last + "'.";
    }

    static synchronized void next() {
        String st = status();
        if (st.equals("won") || st.equals("lost")) nextWord();
        else message = "Finish this word first.";
    }

    // ---------- JSON ----------

    static String arr(Collection<Character> c) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (char ch : c) {
            if (!first) sb.append(',');
            sb.append('"').append(ch).append('"');
            first = false;
        }
        return sb.append(']').toString();
    }

    static synchronized String stateJson() {
        String st = status();
        StringBuilder display = new StringBuilder();
        TreeSet<Character> wrong = new TreeSet<>();

        if (word != null) {
            boolean reveal = st.equals("lost");
            for (char ch : word.toCharArray()) {
                display.append(reveal || guessed.contains(ch) ? ch : '_');
            }
            for (char g : guessed) {
                if (word.indexOf(g) < 0) wrong.add(g);
            }
        }

        return "{"
            + "\"display\":\"" + display + "\","
            + "\"status\":\"" + st + "\","
            + "\"lives\":" + lives + ","
            + "\"max\":" + MAX_LIVES + ","
            + "\"guessed\":" + arr(guessed) + ","
            + "\"wrong\":" + arr(wrong) + ","
            + "\"stack\":" + arr(history) + ","
            + "\"remaining\":" + wordQueue.size() + ","
            + "\"score\":" + score + ","
            + "\"played\":" + played + ","
            + "\"message\":\"" + message + "\""
            + "}";
    }

    // ---------- HTTP ----------

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    static void sendJson(HttpExchange ex) throws IOException {
        send(ex, 200, "application/json; charset=utf-8",
             stateJson().getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws IOException {
        newGame();
        HttpServer server = HttpServer.create(new InetSocketAddress(9090), 0);

        server.createContext("/", ex -> {
            if (!ex.getRequestURI().getPath().equals("/")) {
                send(ex, 404, "text/plain", "Not found".getBytes());
                return;
            }
            try {
                byte[] html = Files.readAllBytes(Paths.get("index.html"));
                send(ex, 200, "text/html; charset=utf-8", html);
            } catch (IOException e) {
                send(ex, 500, "text/plain",
                     "index.html not found. Keep it in the same folder as HangmanWeb.".getBytes());
            }
        });

        server.createContext("/api/state", ex -> sendJson(ex));

        server.createContext("/api/guess", ex -> {
            String q = ex.getRequestURI().getQuery();   // e.g. c=a
            if (q != null && q.startsWith("c=") && q.length() == 3) {
                guess(q.charAt(2));
            }
            sendJson(ex);
        });

        server.createContext("/api/undo", ex -> { undo(); sendJson(ex); });
        server.createContext("/api/next", ex -> { next(); sendJson(ex); });
        server.createContext("/api/restart", ex -> { newGame(); sendJson(ex); });

        server.start();
        System.out.println("Hangman running at http://localhost:9090  (Ctrl+C to stop)");
    }
}
