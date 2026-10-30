import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Simple Snake game.
 * Controls: Arrow keys or WASD.
 * Compile: javac SnakeGame.java
 * Run:     java SnakeGame
 */
public class SnakeGame extends JFrame {

    static final int COLS = 25, ROWS = 25, CELL = 24, DELAY_MS = 100;

    // ---- Scoreboard (lives only as long as the program runs) ----
    static class Entry {
        final String name;
        final int score;
        Entry(String name, int score) { this.name = name; this.score = score; }
    }

    private final List<Entry> scores = new ArrayList<>();
    private final JTextArea scoreArea = new JTextArea();
    private final GamePanel gamePanel = new GamePanel();
    private String playerName;

    public SnakeGame() {
        super("Snake");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        scoreArea.setEditable(false);
        scoreArea.setFocusable(false);
        scoreArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(scoreArea);
        scroll.setPreferredSize(new Dimension(230, ROWS * CELL));
        scroll.setBorder(BorderFactory.createTitledBorder("Scoreboard"));
        scroll.setFocusable(false);

        add(gamePanel, BorderLayout.CENTER);
        add(scroll, BorderLayout.EAST);
        refreshScoreboard();

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);

        // Ask for the first player's name, then begin
        playerName = askName("Enter your name to start:", "");
        if (playerName == null) System.exit(0);
        gamePanel.startGame();
    }

    /** Prompts until a non-empty name is entered. Returns null if the dialog is cancelled. */
    private String askName(String message, String defaultName) {
        while (true) {
            String input = (String) JOptionPane.showInputDialog(
                    this, message, "Snake", JOptionPane.QUESTION_MESSAGE, null, null, defaultName);
            if (input == null) return null;
            input = input.trim();
            if (!input.isEmpty()) {
                return input.length() > 12 ? input.substring(0, 12) : input;
            }
            message = "Name can't be empty. Please enter a name:";
        }
    }

    private void refreshScoreboard() {
        List<Entry> sorted = new ArrayList<>(scores);
        sorted.sort((a, b) -> Integer.compare(b.score, a.score)); // stable: ties keep play order
        StringBuilder sb = new StringBuilder();
        if (sorted.isEmpty()) {
            sb.append(" No scores yet.");
        } else {
            for (int i = 0; i < sorted.size(); i++) {
                Entry e = sorted.get(i);
                sb.append(String.format("%2d. %-12s %4d%n", i + 1, e.name, e.score));
            }
        }
        scoreArea.setText(sb.toString());
        scoreArea.setCaretPosition(0);
    }

    private void onGameOver(int finalScore) {
        scores.add(new Entry(playerName, finalScore));
        refreshScoreboard();

        // Prompt for the next player's name (Cancel quits the program)
        String next = askName("Game over, " + playerName + "! Score: " + finalScore
                + "\n\nEnter a name for the next game (Cancel to quit):", playerName);
        if (next == null) System.exit(0);
        playerName = next;
        gamePanel.startGame();
    }

    // ---- Game panel ----
    class GamePanel extends JPanel {
        private final Deque<Point> snake = new ArrayDeque<>();
        private final Queue<Point> pendingDirs = new ArrayDeque<>();
        private final Random rng = new Random();
        private final Timer timer;
        private Point food = new Point(-1, -1);
        private Point dir = new Point(1, 0);
        private int score = 0;

        GamePanel() {
            setPreferredSize(new Dimension(COLS * CELL, ROWS * CELL));
            setBackground(new Color(20, 24, 28));
            setFocusable(true);

            timer = new Timer(DELAY_MS, e -> tick());

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP:    case KeyEvent.VK_W: queueDir(0, -1); break;
                        case KeyEvent.VK_DOWN:  case KeyEvent.VK_S: queueDir(0, 1);  break;
                        case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: queueDir(-1, 0); break;
                        case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: queueDir(1, 0);  break;
                    }
                }
            });
        }

        void startGame() {
            snake.clear();
            pendingDirs.clear();
            int cx = COLS / 2, cy = ROWS / 2;
            // Head is the first element; start with 3 segments heading right
            snake.addFirst(new Point(cx, cy));
            snake.addLast(new Point(cx - 1, cy));
            snake.addLast(new Point(cx - 2, cy));
            dir = new Point(1, 0);
            score = 0;
            placeFood();
            repaint();
            requestFocusInWindow();
            timer.start();
        }

        /** Queues a direction change, ignoring reversals and duplicates. */
        private void queueDir(int dx, int dy) {
            Point last = pendingDirs.isEmpty() ? dir : ((ArrayDeque<Point>) pendingDirs).peekLast();
            if (last.x == dx && last.y == dy) return;      // same direction
            if (last.x == -dx && last.y == -dy) return;    // 180 degree turn
            if (pendingDirs.size() < 2) pendingDirs.add(new Point(dx, dy));
        }

        private void placeFood() {
            List<Point> free = new ArrayList<>();
            for (int x = 0; x < COLS; x++)
                for (int y = 0; y < ROWS; y++) {
                    Point p = new Point(x, y);
                    if (!snake.contains(p)) free.add(p);
                }
            food = free.isEmpty() ? null : free.get(rng.nextInt(free.size()));
        }

        private void tick() {
            if (!pendingDirs.isEmpty()) dir = pendingDirs.poll();

            Point head = snake.peekFirst();
            Point next = new Point(head.x + dir.x, head.y + dir.y);

            // Wall collision
            if (next.x < 0 || next.x >= COLS || next.y < 0 || next.y >= ROWS) {
                endGame();
                return;
            }

            boolean eating = next.equals(food);
            if (!eating) snake.removeLast(); // tail moves out of the way

            // Self collision
            if (snake.contains(next)) {
                endGame();
                return;
            }

            snake.addFirst(next);
            if (eating) {
                score++;
                placeFood();
                if (food == null) { // board completely filled: you win!
                    endGame();
                    return;
                }
            }
            repaint();
        }

        private void endGame() {
            timer.stop();
            repaint();
            final int finalScore = score;
            SwingUtilities.invokeLater(() -> onGameOver(finalScore));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Food
            if (food != null) {
                g2.setColor(new Color(235, 70, 70));
                g2.fillOval(food.x * CELL + 3, food.y * CELL + 3, CELL - 6, CELL - 6);
            }

            // Snake
            boolean first = true;
            for (Point p : snake) {
                g2.setColor(first ? new Color(120, 230, 120) : new Color(60, 180, 80));
                g2.fillRoundRect(p.x * CELL + 1, p.y * CELL + 1, CELL - 2, CELL - 2, 8, 8);
                first = false;
            }

            // HUD
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g2.drawString((playerName == null ? "" : playerName + "  |  ") + "Score: " + score, 8, 18);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SnakeGame::new);
    }
}