import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kalma - Peer-Support Wellness Companion (Java Swing).
 * Theme: calm sage green with soft pastel cards, rounded corners.
 *
 * Compile and run:  javac Kalma.java && java Kalma
 */
public class Kalma {
    static final String NOTE = "Kalma is not a medical diagnostic system, a crisis-detection system, "
            + "or a replacement for professional counseling. Results are informational only.";
    static final String[] MOODS = {"1 - Very Bad", "2 - Not Good", "3 - Okay", "4 - Good", "5 - Very Good"};

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            DataStore.getInstance().seed();
            new MainWindow().showLogin();
        });
    }
}

/* =====================================================================
 *  MODEL
 * ===================================================================== */

abstract class User {
    private final String id;
    private final String name;
    private final String email;
    private final String password;
    private final String course;
    private boolean active = true;

    protected User(String id, String name, String email, String password, String course) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.course = course;
    }

    String getId() { return id; }
    String getName() { return name; }
    String getEmail() { return email; }
    String getCourse() { return course; }
    boolean isActive() { return active; }
    void setActive(boolean active) { this.active = active; }
    boolean passwordMatches(String attempt) { return password.equals(attempt); }
    String getFirstName() { return name.split(" ")[0]; }
    String getInitials() {
        String[] p = name.trim().split("\\s+");
        return p.length == 1 ? p[0].substring(0, 1).toUpperCase()
                : ("" + p[0].charAt(0) + p[p.length - 1].charAt(0)).toUpperCase();
    }

    abstract String getRole();
    abstract String[] getMenu();
    boolean isStaff() { return false; }
}

final class Student extends User {
    Student(String id, String name, String email, String password, String course) {
        super(id, name, email, password, course);
    }
    @Override String getRole() { return "student"; }
    @Override String[] getMenu() {
        return new String[]{"Home", "Assessment", "Daily Check-In", "Trends", "Resources",
                "Solutions", "Book Consultation", "My Appointments"};
    }
}

final class Counselor extends User {
    Counselor(String id, String name, String email, String password) {
        super(id, name, email, password, "");
    }
    @Override String getRole() { return "counselor"; }
    @Override String[] getMenu() { return new String[]{"Appointments", "Resources"}; }
    @Override boolean isStaff() { return true; }
}

final class Admin extends User {
    Admin(String id, String name, String email, String password) {
        super(id, name, email, password, "");
    }
    @Override String getRole() { return "admin"; }
    @Override String[] getMenu() { return new String[]{"Dashboard", "Users", "Appointments", "Resources"}; }
    @Override boolean isStaff() { return true; }
}

enum Status {
    PENDING("Pending"), CONFIRMED("Confirmed"), COMPLETED("Completed"),
    CANCELLED("Cancelled"), RESCHEDULED("Rescheduled");

    private final String label;
    Status(String label) { this.label = label; }
    @Override public String toString() { return label; }
}

class Appointment {
    private final String studentId;
    private final String counselorId;
    private final String reason;
    private String date;
    private String time;
    private Status status;

    Appointment(String studentId, String counselorId, String date, String time, String reason, Status status) {
        this.studentId = studentId;
        this.counselorId = counselorId;
        this.date = date;
        this.time = time;
        this.reason = reason;
        this.status = status;
    }

    String getStudentId() { return studentId; }
    String getCounselorId() { return counselorId; }
    String getDate() { return date; }
    String getTime() { return time; }
    String getReason() { return reason; }
    Status getStatus() { return status; }
    void setStatus(Status status) { this.status = status; }
    void reschedule(String newDate, String newTime) { this.date = newDate; this.time = newTime; this.status = Status.RESCHEDULED; }
    String getSortKey() { return date + time; }
    boolean occupiesSlot() { return status != Status.CANCELLED; }
    boolean isUpcoming() {
        return status != Status.CANCELLED && status != Status.COMPLETED
                && date.compareTo(LocalDate.now().toString()) >= 0;
    }
}

record CounselorInfo(String id, String name) {}
record Question(String text, String category) {}
record Resource(String title, String category, String description, String contact) {}
record Solution(String category, String title, List<String> steps) {}
record CheckIn(String userId, LocalDate date, int mood, int stress) {}
record AssessmentResult(String userId, LocalDate date, int percent) {}
record ConcernLevel(String label, String message) {}

/* =====================================================================
 *  DATA + SERVICES
 * ===================================================================== */

final class DataStore {
    static final String[] TIME_SLOTS = {"09:00", "10:00", "13:00", "14:00", "15:00"};
    private static final DataStore INSTANCE = new DataStore();

    private final List<User> users = new ArrayList<>();
    private final List<Appointment> appointments = new ArrayList<>();
    private final List<CheckIn> checkIns = new ArrayList<>();
    private final List<AssessmentResult> results = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private final List<Resource> resources = new ArrayList<>();
    private final List<Solution> solutions = new ArrayList<>();
    private final List<CounselorInfo> counselors = List.of(
            new CounselorInfo("c1", "Ms. Angela Reyes, RGC"),
            new CounselorInfo("c2", "Mr. Paolo Dizon, RPsy"));

    private DataStore() {}
    static DataStore getInstance() { return INSTANCE; }

    void seed() {
        if (!users.isEmpty()) return;
        users.add(new Student("u1", "Maria Santos", "student@tip.edu.ph", "student123", "BSCS 3rd Year"));
        users.add(new Student("u2", "Juan Dela Cruz", "juan@tip.edu.ph", "student123", "BSIT 2nd Year"));
        users.add(new Counselor("u3", "Ms. Angela Reyes", "guidance@tip.edu.ph", "guidance123"));
        users.add(new Admin("u4", "System Admin", "admin@tip.edu.ph", "admin123"));

        String[][] q = {
                {"I have felt down or low in mood", "Mood"},
                {"I have felt anxious, tense or on edge", "Stress"},
                {"I felt overwhelmed by my workload or deadlines", "Academic Pressure"},
                {"I worry about my grades or academic standing", "Academic Pressure"},
                {"I have had trouble falling or staying asleep", "Sleep"},
                {"I felt tired even after resting", "Sleep"},
                {"I felt lonely or disconnected from classmates", "Social"},
                {"I found it hard to adjust to college life", "Social"},
                {"I had trouble concentrating on tasks", "Stress"},
                {"Overall, I have not felt well or balanced lately", "Well-being"}};
        for (String[] x : q) questions.add(new Question(x[0], x[1]));

        resources.add(new Resource("TIP QC Guidance Office", "Guidance Office",
                "Walk-in or book a consultation. Ask the front desk for the room.", "guidance@tip.edu.ph"));
        resources.add(new Resource("Peer Tutoring Program", "Academic Support",
                "Get help with difficult subjects from fellow students.", "Ask your department"));
        resources.add(new Resource("Sleep Hygiene Basics", "Wellness Materials",
                "Simple habits for better rest.", "Guidance Office"));
        resources.add(new Resource("5-Minute Breathing Exercise", "Self-Help",
                "Inhale 4 counts, hold 4, exhale 6. Repeat for five minutes.", "Self-guided"));
        resources.add(new Resource("NCMH Crisis Hotline", "Important Contacts",
                "24/7 national mental health crisis line. Please verify numbers with the Guidance Office.",
                "1553 / 0917-899-8727"));
        resources.add(new Resource("Emergency Services (PH)", "Important Contacts", "For immediate danger.", "911"));

        solutions.add(new Solution("Academic Pressure", "Break big tasks down", List.of(
                "List every deliverable", "Split each into 25-minute steps", "Do the smallest one first",
                "Ask a professor about extensions early")));
        solutions.add(new Solution("Stress", "Reset your body", List.of(
                "Try slow breathing for 5 minutes", "Take a short walk", "Drink water",
                "Write down what is worrying you")));
        solutions.add(new Solution("Sleep", "Protect your rest", List.of(
                "Keep a steady sleep time", "Stop screens 30 minutes before bed", "Avoid late caffeine")));
        solutions.add(new Solution("Social", "Reach out small", List.of(
                "Message one classmate", "Join a peer circle", "Attend one campus activity this week")));

        int[] m = {3, 3, 2, 3, 4, 3, 4};
        int[] s = {4, 4, 5, 3, 3, 3, 2};
        for (int i = 0; i < 7; i++) checkIns.add(new CheckIn("u1", LocalDate.now().minusDays(6 - i), m[i], s[i]));

        appointments.add(new Appointment("u1", "c1", LocalDate.now().minusDays(20).toString(), "10:00",
                "Academic pressure", Status.COMPLETED));
        appointments.add(new Appointment("u2", "c1", nextWeekdays(2).get(1), "13:00",
                "College adjustment", Status.PENDING));
    }

    // ----- users -----
    List<User> getUsers() { return Collections.unmodifiableList(users); }
    void addUser(User u) { users.add(u); }
    Optional<User> findByEmail(String email) {
        return users.stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
    }
    Optional<User> findById(String id) { return users.stream().filter(u -> u.getId().equals(id)).findFirst(); }
    String nameOf(String userId) { return findById(userId).map(User::getName).orElse("Unknown"); }
    String courseOf(String userId) { return findById(userId).map(User::getCourse).orElse(""); }

    // ----- counselors -----
    List<CounselorInfo> getCounselors() { return counselors; }
    String counselorName(String id) {
        return counselors.stream().filter(c -> c.id().equals(id)).map(CounselorInfo::name).findFirst().orElse("?");
    }

    // ----- appointments -----
    List<Appointment> getAppointments() { return Collections.unmodifiableList(appointments); }
    void addAppointment(Appointment a) { appointments.add(a); }
    List<Appointment> appointmentsFor(User u) {
        return appointments.stream().filter(a -> u.isStaff() || a.getStudentId().equals(u.getId()))
                .sorted(Comparator.comparing(Appointment::getSortKey)).collect(Collectors.toList());
    }
    List<String> freeSlots(String counselorId, String date) {
        Set<String> taken = appointments.stream()
                .filter(a -> a.getCounselorId().equals(counselorId) && a.getDate().equals(date) && a.occupiesSlot())
                .map(Appointment::getTime).collect(Collectors.toSet());
        return Arrays.stream(TIME_SLOTS).filter(t -> !taken.contains(t)).collect(Collectors.toList());
    }
    static List<String> nextWeekdays(int n) {
        List<String> out = new ArrayList<>();
        LocalDate d = LocalDate.now();
        while (out.size() < n) {
            d = d.plusDays(1);
            if (d.getDayOfWeek().getValue() < 6) out.add(d.toString());
        }
        return out;
    }

    // ----- check-ins and assessments -----
    List<CheckIn> getCheckIns() { return Collections.unmodifiableList(checkIns); }
    List<CheckIn> checkInsOf(String userId) {
        return checkIns.stream().filter(c -> c.userId().equals(userId)).collect(Collectors.toList());
    }
    void saveCheckIn(CheckIn c) {
        checkIns.removeIf(x -> x.userId().equals(c.userId()) && x.date().equals(c.date()));
        checkIns.add(c);
    }
    List<AssessmentResult> getResults() { return Collections.unmodifiableList(results); }
    List<AssessmentResult> resultsOf(String userId) {
        return results.stream().filter(r -> r.userId().equals(userId)).collect(Collectors.toList());
    }
    void addResult(AssessmentResult r) { results.add(r); }

    // ----- content -----
    List<Question> getQuestions() { return questions; }
    List<Resource> getResources() { return resources; }
    List<Solution> getSolutions() { return solutions; }
}

final class AssessmentService {
    static final String[] OPTIONS = {"Never", "Sometimes", "Often", "Almost always"};
    private AssessmentService() {}

    static int percent(int total, int questionCount) {
        return Math.round(total * 100f / (questionCount * 3));
    }

    static ConcernLevel classify(int p) {
        if (p < 25) return new ConcernLevel("Low Concern",
                "Your answers suggest few current difficulties. Keep up habits that help you feel balanced.");
        if (p < 50) return new ConcernLevel("Mild Concern",
                "Some areas may be weighing on you. Small changes and talking with peers can help.");
        if (p < 75) return new ConcernLevel("Moderate Concern",
                "Several areas seem to be affecting you. Consider the suggested solutions and speaking with the Guidance Office.");
        return new ConcernLevel("High Concern",
                "Many areas seem to be affecting you. Please consider reaching out to the Guidance Office or a trusted person soon.");
    }
}

/* =====================================================================
 *  THEME + REUSABLE UI COMPONENTS
 * ===================================================================== */

/** Panel whose maximum height equals its preferred height, so BoxLayout does not stretch it vertically. */
class HugPanel extends JPanel {
    HugPanel(LayoutManager layout) { super(layout); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
}

/** Rounded panel with optional border and optional gradient. Hugs its preferred height. */
class RoundPanel extends JPanel {
    private final Color fill, fill2, line;
    private final int arc;

    RoundPanel(LayoutManager layout, Color fill, Color line, int arc) { this(layout, fill, null, line, arc); }

    RoundPanel(LayoutManager layout, Color fill, Color fill2, Color line, int arc) {
        super(layout);
        this.fill = fill;
        this.fill2 = fill2;
        this.line = line;
        this.arc = arc;
        setOpaque(false);
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(fill2 == null ? fill : new GradientPaint(0, 0, fill, getWidth(), getHeight(), fill2));
        g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        if (line != null) {
            g.setColor(line);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        g.dispose();
        super.paintComponent(g0);
    }
}

/** Pill-shaped button with hover state. Colors can be changed later (used by the sidebar). */
class RoundButton extends JButton {
    private Color bg, hover;
    private final Color border;

    RoundButton(String text, Color bg, Color hover, Color fg, Color border) {
        super(text);
        this.bg = bg;
        this.hover = hover;
        this.border = border;
        setForeground(fg);
        setFont(Theme.BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(9, 20, 9, 20));
    }

    void setColors(Color bg, Color hover, Color fg) {
        this.bg = bg;
        this.hover = hover;
        setForeground(fg);
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getModel().isPressed() ? hover.darker() : getModel().isRollover() ? hover : bg);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        if (border != null) {
            g.setColor(border);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        }
        g.dispose();
        super.paintComponent(g0);
    }
}

/** Selectable rounded "pill" used for mood, stress and period choices on the Home page. */
class PillToggle extends JToggleButton {
    PillToggle(String text) {
        super(text);
        setFont(Theme.BASE.deriveFont(12f));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(7, 14, 7, 14));
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean on = isSelected();
        g.setColor(on ? Theme.INK : getModel().isRollover() ? Theme.MINT : Theme.WHITE);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        if (!on) {
            g.setColor(Theme.GRID);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        }
        g.setFont(getFont());
        g.setColor(on ? Theme.WHITE : Theme.INK);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}

/** Solid circle used as the logo mark. */
class Dot extends JComponent {
    private final Color color;
    Dot(Color color, int size) {
        this.color = color;
        setPreferredSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fillOval(0, 0, getWidth(), getHeight());
        g.dispose();
    }
}

/** Circle with the user's initials. */
class Avatar extends JComponent {
    private final String initials;
    Avatar(String initials) {
        this.initials = initials;
        Dimension d = new Dimension(38, 38);
        setPreferredSize(d);
        setMaximumSize(d);
        setMinimumSize(d);
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.GREEN_DARK);
        g.fillOval(0, 0, getWidth(), getHeight());
        g.setColor(Theme.WHITE);
        g.setFont(Theme.BOLD);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(initials, (getWidth() - fm.stringWidth(initials)) / 2,
                (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}

/** Kalma theme: sage green with soft pastel accents. */
final class Theme {
    static final Color GREEN_DARK = new Color(0x2F5D50);
    static final Color GREEN = new Color(0x5E9C76);
    static final Color SAGE = new Color(0xE3EFE7);       // sidebar
    static final Color SAGE_SEL = new Color(0xCFE4D6);   // selected nav item
    static final Color BG = new Color(0xF1F6F2);         // page background
    static final Color MINT = new Color(0xDCEBE0);
    static final Color PEACH = new Color(0xF9E8DD);
    static final Color SKY = new Color(0xE3E9F8);
    static final Color LAVENDER = new Color(0xEAE4F5);
    static final Color ORANGE = new Color(0xD9814E);
    static final Color INK = new Color(0x1F3A33);
    static final Color WHITE = Color.WHITE;
    static final Color GRID = new Color(0xD3E2D8);
    static final Color MUTED = new Color(0x6B7F76);
    static final Color[] PASTELS = {MINT, PEACH, SKY, LAVENDER};

    static final Font BASE = new Font("SansSerif", Font.PLAIN, 13);
    static final Font BOLD = BASE.deriveFont(Font.BOLD);
    static final Font TITLE = new Font("SansSerif", Font.BOLD, 22);

    private Theme() {}

    @SuppressWarnings("deprecation")
    static void install() {
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
                @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(GREEN_DARK); }
                @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(GREEN); }
                @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(MINT); }
                @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(0x9DB3A6); }
                @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(0xC9D8CE); }
                @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(WHITE); }
            });
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) { }

        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, new FontUIResource(BASE));
        }
        UIManager.put("Panel.background", WHITE);
        UIManager.put("OptionPane.background", WHITE);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Button.background", GREEN_DARK);
        UIManager.put("Button.foreground", WHITE);
        UIManager.put("Button.select", GREEN);
        UIManager.put("Button.focus", GREEN);
        UIManager.put("ComboBox.background", WHITE);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("ComboBox.selectionBackground", MINT);
        UIManager.put("ComboBox.selectionForeground", INK);
        UIManager.put("TextField.selectionBackground", MINT);
        UIManager.put("TextField.selectionForeground", INK);
        UIManager.put("PasswordField.selectionBackground", MINT);
        UIManager.put("PasswordField.selectionForeground", INK);
        UIManager.put("Slider.background", BG);
        UIManager.put("Slider.foreground", GREEN_DARK);
        UIManager.put("Slider.focus", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Viewport.background", BG);
    }

    // ----- text -----
    static JLabel heading(String s) {
        JLabel l = new JLabel(s);
        l.setFont(TITLE);
        l.setForeground(GREEN_DARK);
        l.setBorder(new CompoundBorder(new MatteBorder(0, 0, 3, 0, GREEN), new EmptyBorder(0, 0, 4, 0)));
        return l;
    }

    static JLabel subheading(String s) {
        JLabel l = new JLabel(s);
        l.setFont(BASE.deriveFont(Font.BOLD, 15f));
        l.setForeground(GREEN_DARK);
        return l;
    }

    static JLabel fieldLabel(String s) {
        JLabel l = new JLabel(s);
        l.setFont(BOLD.deriveFont(12f));
        l.setForeground(INK);
        return l;
    }

    static JLabel wrapLabel(String s, int width, Color color, float size, boolean bold) {
        JLabel l = new JLabel("<html><body style='width:" + width + "px'>" + s + "</body></html>");
        l.setFont(BASE.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    static JTextArea text(String s) {
        JTextArea a = new JTextArea(s) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        a.setColumns(50);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setFont(BASE);
        a.setForeground(INK);
        a.setBorder(null);
        return a;
    }

    /** Soft mint callout box. */
    static JPanel notice(String s) {
        RoundPanel p = new RoundPanel(new BorderLayout(), MINT, null, 20);
        p.setBorder(new EmptyBorder(14, 18, 14, 18));
        p.add(text(s), BorderLayout.CENTER);
        return p;
    }

    /** Small summary card with a pastel background. */
    static JPanel card(String title, String value, Color bg) {
        RoundPanel p = new RoundPanel(null, bg, null, 20);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel t = new JLabel(title.toUpperCase());
        t.setFont(BOLD.deriveFont(11f));
        t.setForeground(MUTED);
        JLabel v = wrapLabel(value, 170, INK, 15f, true);
        t.setAlignmentX(0f);
        v.setAlignmentX(0f);
        p.add(t);
        p.add(Box.createVerticalStrut(6));
        p.add(v);
        return p;
    }

    // ----- inputs -----
    static JTextField field() {
        JTextField f = new JTextField(22);
        styleInput(f);
        return f;
    }

    static JPasswordField passwordField() {
        JPasswordField f = new JPasswordField(22);
        styleInput(f);
        return f;
    }

    private static void styleInput(JTextField f) {
        f.setBorder(new CompoundBorder(new LineBorder(GRID, 1, true), new EmptyBorder(7, 10, 7, 10)));
        f.setBackground(WHITE);
        f.setForeground(INK);
        f.setCaretColor(GREEN_DARK);
    }

    static JComboBox<String> combo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setBackground(WHITE);
        c.setForeground(INK);
        return c;
    }

    // ----- buttons -----
    static JButton primary(String text, Runnable action) {
        RoundButton b = new RoundButton(text, GREEN_DARK, GREEN, WHITE, null);
        b.addActionListener(e -> action.run());
        return b;
    }

    static JButton secondary(String text, Runnable action) {
        RoundButton b = new RoundButton(text, WHITE, MINT, GREEN_DARK, GREEN_DARK);
        b.addActionListener(e -> action.run());
        return b;
    }

    static JButton navButton(String text, Runnable action) {
        RoundButton b = new RoundButton(text, SAGE, SAGE_SEL, INK, null);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(10, 14, 10, 14));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setAlignmentX(0f);
        b.addActionListener(e -> action.run());
        setNavSelected(b, false);
        return b;
    }

    static void setNavSelected(JButton b, boolean selected) {
        ((RoundButton) b).setColors(selected ? SAGE_SEL : SAGE, selected ? SAGE_SEL : new Color(0xD3E6D9),
                selected ? GREEN_DARK : INK);
    }

    // ----- tables -----
    static JTable table(String[] head, List<Object[]> rows, int... widths) {
        DefaultTableModel m = new DefaultTableModel(head, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        rows.forEach(m::addRow);
        JTable t = new JTable(m);
        t.setRowHeight(30);
        t.setGridColor(GRID);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setBackground(WHITE);
        t.setForeground(INK);
        t.setSelectionBackground(MINT);
        t.setSelectionForeground(INK);
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setBackground(SAGE);
        h.setForeground(GREEN_DARK);
        h.setFont(BOLD);
        for (int i = 0; i < widths.length && i < head.length; i++) t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        return t;
    }

    static JScrollPane scroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(new LineBorder(GRID, 1));
        sp.getViewport().setBackground(WHITE);
        sp.setPreferredSize(new Dimension(600, 260));
        return sp;
    }
}

/** Line chart of mood (green) and stress (orange) check-ins. */
class TrendChart extends JPanel {
    private List<CheckIn> data = new ArrayList<>();

    TrendChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(600, 280));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
    }

    void setData(List<CheckIn> data) { this.data = data; repaint(); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Theme.WHITE);
        g.fillRoundRect(0, 0, w, h, 20, 20);
        g.setColor(Theme.GRID);
        g.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);

        g.setFont(Theme.BASE.deriveFont(11f));
        g.setColor(Theme.GREEN);
        g.fillOval(18, 14, 8, 8);
        g.setColor(Theme.MUTED);
        g.drawString("Mood", 30, 22);
        g.setColor(Theme.ORANGE);
        g.fillOval(76, 14, 8, 8);
        g.setColor(Theme.MUTED);
        g.drawString("Stress", 88, 22);

        int left = 40, right = 20, top = 40, bottom = 30;
        int pw = w - left - right, ph = h - top - bottom;
        for (int v = 1; v <= 5; v++) {
            int y = top + ph - (v - 1) * ph / 4;
            g.setColor(Theme.GRID);
            g.drawLine(left, y, w - right, y);
            g.setColor(Theme.MUTED);
            g.drawString(String.valueOf(v), 22, y + 4);
        }
        if (data.isEmpty()) {
            g.setColor(Theme.INK);
            g.drawString("No check-ins in this period.", left + 10, top + 24);
            g.dispose();
            return;
        }
        int n = data.size();
        int[] xs = new int[n], ms = new int[n], ss = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = n == 1 ? left + pw / 2 : left + 16 + i * (pw - 32) / (n - 1);
            ms[i] = top + ph - (data.get(i).mood() - 1) * ph / 4;
            ss[i] = top + ph - (data.get(i).stress() - 1) * ph / 4;
        }
        drawSeries(g, xs, ms, Theme.GREEN);
        drawSeries(g, xs, ss, Theme.ORANGE);
        g.setColor(Theme.MUTED);
        for (int i = 0; i < n; i++) {
            if (n <= 10 || i % (n / 8 + 1) == 0) {
                String d = data.get(i).date().toString().substring(5);
                g.drawString(d, xs[i] - g.getFontMetrics().stringWidth(d) / 2, h - 10);
            }
        }
        g.dispose();
    }

    private void drawSeries(Graphics2D g, int[] xs, int[] ys, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < xs.length; i++) g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
        for (int i = 0; i < xs.length; i++) g.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
        g.setStroke(new BasicStroke(1f));
    }
}

/* =====================================================================
 *  VIEWS
 * ===================================================================== */

abstract class View extends JPanel implements Scrollable {
    protected final MainWindow window;

    protected View(MainWindow window) {
        this.window = window;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));
    }

    /** Adds components top-to-bottom, left aligned, with consistent spacing. */
    protected void put(Component... components) {
        for (Component c : components) {
            ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            add(c);
            JComponent gap = (JComponent) Box.createVerticalStrut(12);
            gap.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(gap);
        }
    }

    protected User user() { return window.getUser(); }
    protected DataStore store() { return DataStore.getInstance(); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 64; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}

/** Home page: greeting banner, stat cards, quick check-in, trends, and a right-hand info column. */
class DashboardView extends View {
    private static final String[] STRESS = {"Very Low", "Low", "Moderate", "High", "Very High"};
    private static final String[] AFFECTS = {"Mood", "Stress", "Academic Pressure", "Sleep", "Social", "Well-being"};

    DashboardView(MainWindow w) {
        super(w);
        String uid = user().getId();
        Optional<CheckIn> lastCheckIn = store().checkInsOf(uid).stream().max(Comparator.comparing(CheckIn::date));
        Optional<AssessmentResult> lastResult = store().resultsOf(uid).stream().max(Comparator.comparing(AssessmentResult::date));
        Optional<Appointment> next = store().getAppointments().stream()
                .filter(a -> a.getStudentId().equals(uid) && a.isUpcoming())
                .min(Comparator.comparing(Appointment::getSortKey));

        JPanel left = column();
        left.add(banner());
        left.add(gap(14));
        left.add(statRow(lastCheckIn, lastResult, next));
        left.add(gap(14));
        JPanel mid = new HugPanel(new GridLayout(1, 2, 14, 0));
        mid.setOpaque(false);
        mid.setAlignmentX(0f);
        mid.add(quickCheckIn());
        mid.add(trendsCard());
        left.add(mid);

        JPanel right = column();
        right.add(rememberCard());
        right.add(gap(14));
        right.add(appointmentCard(next));
        right.add(gap(14));
        right.add(quickActions());

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.setOpaque(false);
        leftWrap.add(left, BorderLayout.NORTH);
        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setOpaque(false);
        rightWrap.add(right, BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout(16, 0));
        main.setOpaque(false);
        main.add(leftWrap, BorderLayout.CENTER);
        main.add(rightWrap, BorderLayout.EAST);
        put(main);
    }

    // ----- helpers -----
    private static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static Component gap(int h) {
        JComponent c = (JComponent) Box.createVerticalStrut(h);
        c.setAlignmentX(0f);
        return c;
    }

    /** White rounded card with a bold title. Children are added with add(). */
    private static RoundPanel card(String title) {
        RoundPanel p = new RoundPanel(null, Theme.WHITE, Theme.GRID, 20);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16, 18, 16, 18));
        p.setAlignmentX(0f);
        if (title != null) {
            JLabel t = new JLabel(title);
            t.setFont(Theme.BASE.deriveFont(Font.BOLD, 16f));
            t.setForeground(Theme.INK);
            t.setAlignmentX(0f);
            p.add(t);
            p.add(gap(10));
        }
        return p;
    }

    private static JPanel row(int hgap) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, hgap, 0));
        p.setOpaque(false);
        p.setAlignmentX(0f);
        return p;
    }

    private static JLabel small(String s, Color c) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.BASE.deriveFont(12f));
        l.setForeground(c);
        l.setAlignmentX(0f);
        return l;
    }

    private static JLabel boldLabel(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.BOLD);
        l.setForeground(Theme.INK);
        l.setBorder(new EmptyBorder(10, 0, 6, 0));
        l.setAlignmentX(0f);
        return l;
    }

    // ----- banner -----
    private JComponent banner() {
        int hour = LocalTime.now().getHour();
        String greet = hour < 12 ? "Good morning," : hour < 18 ? "Good afternoon," : "Good evening,";
        RoundPanel b = new RoundPanel(new BorderLayout(20, 0), Theme.WHITE, Theme.GRID, 20);
        b.setBorder(new EmptyBorder(22, 26, 22, 26));
        b.setAlignmentX(0f);

        JPanel l = column();
        JLabel g1 = new JLabel(greet);
        g1.setFont(Theme.BASE.deriveFont(15f));
        JLabel g2 = new JLabel(user().getFirstName() + "!");
        g2.setFont(new Font("SansSerif", Font.BOLD, 30));
        g2.setForeground(Theme.INK);
        JLabel g3 = small("Take a deep breath. You're doing better than you think.", Theme.MUTED);
        JButton breathe = Theme.secondary("Try the breathing guide", () -> window.message(
                "Breathe in for 4 counts, hold for 4, breathe out for 6.\nRepeat for five minutes."));
        g1.setAlignmentX(0f);
        g2.setAlignmentX(0f);
        breathe.setAlignmentX(0f);
        l.add(g1);
        l.add(g2);
        l.add(Box.createVerticalStrut(4));
        l.add(g3);
        l.add(Box.createVerticalStrut(12));
        l.add(breathe);

        JLabel quote = Theme.wrapLabel("Small steps still lead to big progress.", 260, Theme.GREEN_DARK, 24f, false);
        quote.setHorizontalAlignment(SwingConstants.RIGHT);
        b.add(l, BorderLayout.CENTER);
        b.add(quote, BorderLayout.EAST);
        return b;
    }

    // ----- stat cards -----
    private JComponent statRow(Optional<CheckIn> ci, Optional<AssessmentResult> ar, Optional<Appointment> ap) {
        JPanel r = new HugPanel(new GridLayout(1, 4, 12, 0));
        r.setOpaque(false);
        r.setAlignmentX(0f);
        r.add(stat("Latest Mood", ci.map(c -> Kalma.MOODS[c.mood() - 1].substring(4)).orElse("None yet"),
                ci.map(c -> c.date().toString()).orElse(""), -1));
        r.add(stat("Stress Level", ci.map(c -> STRESS[c.stress() - 1]).orElse("None yet"),
                ci.map(c -> c.date().toString()).orElse(""), ci.map(CheckIn::stress).orElse(-1)));
        r.add(stat("Last Assessment", ar.map(a -> AssessmentService.classify(a.percent()).label()).orElse("Not taken yet"),
                ar.map(a -> a.date().toString()).orElse(""), -1));
        r.add(stat("Upcoming Appointment", ap.map(a -> a.getDate() + " " + a.getTime()).orElse("None scheduled"),
                ap.map(a -> "Guidance Office (In-Person)").orElse(""), -1));
        return r;
    }

    private JPanel stat(String title, String value, String sub, int stressLevel) {
        RoundPanel p = card(null);
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        p.add(small(title, Theme.MUTED));
        p.add(gap(4));
        JLabel v = Theme.wrapLabel(value, 110, Theme.INK, 15f, true);
        v.setAlignmentX(0f);
        p.add(v);
        if (stressLevel > 0) {
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(stressLevel * 20);
            bar.setForeground(Theme.ORANGE);
            bar.setBackground(Theme.GRID);
            bar.setBorder(null);
            bar.setBorderPainted(false);
            bar.setPreferredSize(new Dimension(100, 5));
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
            bar.setAlignmentX(0f);
            p.add(gap(4));
            p.add(bar);
        }
        if (!sub.isEmpty()) {
            p.add(gap(4));
            p.add(small(sub, Theme.MUTED));
        }
        return p;
    }

    // ----- quick check-in -----
    private JPanel quickCheckIn() {
        RoundPanel p = card("Quick Check-In");
        p.add(small("How are you feeling today?", Theme.MUTED));
        p.add(gap(8));
        p.add(boldLabel("Mood"));
        ButtonGroup moodGroup = new ButtonGroup();
        JPanel moods = row(6);
        for (int i = 0; i < Kalma.MOODS.length; i++) {
            PillToggle t = new PillToggle(Kalma.MOODS[i].substring(4));
            t.setSelected(i == 3);
            moodGroup.add(t);
            moods.add(t);
        }
        p.add(moods);
        p.add(boldLabel("Stress Level"));
        ButtonGroup stressGroup = new ButtonGroup();
        JPanel stress = row(6);
        for (int i = 0; i < STRESS.length; i++) {
            PillToggle t = new PillToggle(STRESS[i]);
            t.setSelected(i == 2);
            stressGroup.add(t);
            stress.add(t);
        }
        p.add(stress);
        p.add(boldLabel("What's affecting you? (optional)"));
        JPanel boxes = new JPanel(new GridLayout(0, 3, 4, 2));
        boxes.setOpaque(false);
        boxes.setAlignmentX(0f);
        for (String a : AFFECTS) {
            JCheckBox cb = new JCheckBox(a);
            cb.setOpaque(false);
            cb.setForeground(Theme.INK);
            boxes.add(cb);
        }
        p.add(boxes);
        p.add(gap(10));
        JButton save = Theme.primary("Save Check-In", () -> {
            int mood = indexOf(moodGroup) + 1, st = indexOf(stressGroup) + 1;
            store().saveCheckIn(new CheckIn(user().getId(), LocalDate.now(), mood, st));
            window.message("Check-in saved.");
            window.refresh();
        });
        save.setAlignmentX(0f);
        p.add(save);
        return p;
    }

    private static int indexOf(ButtonGroup g) {
        int i = 0;
        for (Enumeration<AbstractButton> e = g.getElements(); e.hasMoreElements(); i++)
            if (e.nextElement().isSelected()) return i;
        return 0;
    }

    // ----- trends -----
    private JPanel trendsCard() {
        RoundPanel p = card(null);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setAlignmentX(0f);
        JLabel t = new JLabel("Wellness Trends");
        t.setFont(Theme.BASE.deriveFont(Font.BOLD, 16f));
        t.setForeground(Theme.INK);
        JButton all = new JButton("View All");
        all.setBorderPainted(false);
        all.setContentAreaFilled(false);
        all.setFocusPainted(false);
        all.setForeground(Theme.GREEN_DARK);
        all.setFont(Theme.BASE.deriveFont(12f));
        all.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        all.addActionListener(e -> window.open("Trends"));
        head.add(t, BorderLayout.WEST);
        head.add(all, BorderLayout.EAST);
        p.add(head);
        p.add(gap(8));

        TrendChart chart = new TrendChart();
        chart.setPreferredSize(new Dimension(300, 230));
        chart.setAlignmentX(0f);
        String[] names = {"7 Days", "30 Days", "Semester"};
        int[] days = {7, 30, 0};
        ButtonGroup g = new ButtonGroup();
        JPanel pills = row(6);
        for (int i = 0; i < names.length; i++) {
            PillToggle b = new PillToggle(names[i]);
            int d = days[i];
            b.addActionListener(e -> chart.setData(store().checkInsOf(user().getId()).stream()
                    .filter(c -> d == 0 || !c.date().isBefore(LocalDate.now().minusDays(d - 1)))
                    .sorted(Comparator.comparing(CheckIn::date)).collect(Collectors.toList())));
            b.setSelected(i == 0);
            g.add(b);
            pills.add(b);
        }
        p.add(pills);
        p.add(gap(8));
        p.add(chart);
        chart.setData(store().checkInsOf(user().getId()).stream()
                .filter(c -> !c.date().isBefore(LocalDate.now().minusDays(6)))
                .sorted(Comparator.comparing(CheckIn::date)).collect(Collectors.toList()));
        return p;
    }

    // ----- right column -----
    private JPanel rememberCard() {
        RoundPanel p = card("Remember");
        JLabel body = Theme.wrapLabel(Kalma.NOTE, 250, Theme.INK, 13f, false);
        body.setAlignmentX(0f);
        p.add(body);
        p.add(gap(10));
        JButton more = Theme.secondary("Learn More", () -> window.message(Kalma.NOTE
                + "\n\nNeed to talk to someone now? NCMH crisis hotline 1553 or 0917-899-8727. Emergencies: 911."));
        more.setAlignmentX(0f);
        p.add(more);
        return p;
    }

    private JPanel appointmentCard(Optional<Appointment> next) {
        RoundPanel p = card("Upcoming Appointment");
        if (next.isEmpty()) {
            p.add(small("None scheduled.", Theme.MUTED));
            p.add(gap(10));
            JButton book = Theme.secondary("Book Consultation", () -> window.open("Book Consultation"));
            book.setAlignmentX(0f);
            p.add(book);
            return p;
        }
        Appointment a = next.get();
        RoundPanel inner = new RoundPanel(null, Theme.BG, null, 14);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(new EmptyBorder(12, 12, 12, 12));
        inner.setAlignmentX(0f);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setAlignmentX(0f);
        JLabel kind = new JLabel("In-Person Consultation");
        kind.setFont(Theme.BOLD);
        kind.setForeground(Theme.INK);
        JLabel status = new JLabel(" " + a.getStatus() + " ");
        status.setOpaque(true);
        status.setBackground(Theme.MINT);
        status.setForeground(Theme.GREEN_DARK);
        status.setFont(Theme.BASE.deriveFont(Font.BOLD, 11f));
        top.add(kind, BorderLayout.WEST);
        top.add(status, BorderLayout.EAST);
        inner.add(top);
        inner.add(gap(6));
        inner.add(small(a.getDate() + " \u00B7 " + a.getTime(), Theme.MUTED));
        inner.add(gap(3));
        inner.add(small("TIP QC Guidance Office", Theme.MUTED));
        inner.add(gap(3));
        inner.add(small(store().counselorName(a.getCounselorId()), Theme.MUTED));
        p.add(inner);
        p.add(gap(10));
        JButton details = Theme.secondary("View Details \u203A", () -> window.open("My Appointments"));
        details.setAlignmentX(0f);
        p.add(details);
        return p;
    }

    private JPanel quickActions() {
        RoundPanel p = card("Quick Actions");
        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 8));
        grid.setOpaque(false);
        grid.setAlignmentX(0f);
        String[][] items = {{"Assessment", "Assessment"}, {"Check-In", "Daily Check-In"}, {"Solutions", "Solutions"},
                {"Resources", "Resources"}, {"Trends", "Trends"}, {"Book", "Book Consultation"}};
        for (String[] it : items) {
            RoundButton b = new RoundButton(it[0], Theme.BG, Theme.MINT, Theme.INK, Theme.GRID);
            b.setFont(Theme.BASE.deriveFont(12f));
            b.setBorder(new EmptyBorder(24, 6, 24, 6));
            b.addActionListener(e -> window.open(it[1]));
            grid.add(b);
        }
        p.add(grid);
        return p;
    }
}

class AssessmentView extends View {
    AssessmentView(MainWindow w) {
        super(w);
        List<Object[]> rows = store().resultsOf(user().getId()).stream()
                .sorted(Comparator.comparing(AssessmentResult::date).reversed())
                .map(r -> new Object[]{r.date(), AssessmentService.classify(r.percent()).label() + " (" + r.percent() + "%)"})
                .collect(Collectors.toList());
        put(Theme.heading("Wellness Assessment"),
                Theme.text(store().getQuestions().size() + " short questions on mood, stress, academics, sleep and social life. "
                        + Kalma.NOTE),
                Theme.primary("Start assessment", this::run),
                Theme.subheading("Your results"),
                Theme.scroll(Theme.table(new String[]{"Date", "Result"}, rows)));
    }

    private void run() {
        List<Question> qs = store().getQuestions();
        int total = 0;
        for (int i = 0; i < qs.size(); i++) {
            int r = JOptionPane.showOptionDialog(window, "In the past 2 weeks: " + qs.get(i).text(),
                    "Question " + (i + 1) + " of " + qs.size(), JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, AssessmentService.OPTIONS, null);
            if (r < 0) return;
            total += r;
        }
        int pct = AssessmentService.percent(total, qs.size());
        store().addResult(new AssessmentResult(user().getId(), LocalDate.now(), pct));
        ConcernLevel level = AssessmentService.classify(pct);
        window.message(level.label() + " (" + pct + "%)\n\n" + level.message()
                + "\n\nInformational only, not a diagnosis."
                + (pct >= 75 ? "\nIf you feel unsafe, contact NCMH 1553 or emergency services 911." : ""));
        window.refresh();
    }
}

class CheckInView extends View {
    CheckInView(MainWindow w) {
        super(w);
        JComboBox<String> mood = Theme.combo(Kalma.MOODS);
        mood.setSelectedIndex(2);
        JSlider stress = new JSlider(1, 5, 3);
        stress.setMajorTickSpacing(1);
        stress.setPaintLabels(true);
        stress.setPaintTicks(true);
        stress.setSnapToTicks(true);
        stress.setOpaque(false);
        List<Object[]> rows = store().checkInsOf(user().getId()).stream()
                .sorted(Comparator.comparing(CheckIn::date).reversed()).limit(15)
                .map(c -> new Object[]{c.date(), c.mood(), c.stress()}).collect(Collectors.toList());
        put(Theme.heading("Daily Check-In"),
                Theme.fieldLabel("How is your mood?"), mood,
                Theme.fieldLabel("Stress level (1 low - 5 high)"), stress,
                Theme.primary("Save check-in", () -> {
                    store().saveCheckIn(new CheckIn(user().getId(), LocalDate.now(), mood.getSelectedIndex() + 1, stress.getValue()));
                    window.message("Check-in saved.");
                    window.open("Trends");
                }),
                Theme.subheading("Previous check-ins"),
                Theme.scroll(Theme.table(new String[]{"Date", "Mood", "Stress"}, rows)));
    }
}

class TrendsView extends View {
    TrendsView(MainWindow w) {
        super(w);
        JComboBox<String> period = Theme.combo(new String[]{"7 days", "30 days", "Semester"});
        TrendChart chart = new TrendChart();
        Runnable draw = () -> {
            int days = new int[]{7, 30, 0}[period.getSelectedIndex()];
            chart.setData(store().checkInsOf(user().getId()).stream()
                    .filter(c -> days == 0 || !c.date().isBefore(LocalDate.now().minusDays(days - 1)))
                    .sorted(Comparator.comparing(CheckIn::date)).collect(Collectors.toList()));
        };
        period.addActionListener(e -> draw.run());
        draw.run();
        put(Theme.heading("Wellness Trends"), period,
                Theme.text("Green = mood, orange = stress (scale of 1 to 5)."), chart);
    }
}

class ResourcesView extends View {
    private final JPanel holder = new JPanel(new BorderLayout());

    ResourcesView(MainWindow w) {
        super(w);
        JTextField query = Theme.field();
        JComboBox<String> category = Theme.combo(new String[]{"All", "Guidance Office", "Student Support",
                "Academic Support", "Wellness Materials", "Self-Help", "Important Contacts"});
        holder.setOpaque(false);
        Runnable draw = () -> {
            String k = query.getText().toLowerCase();
            String c = (String) category.getSelectedItem();
            List<Object[]> rows = store().getResources().stream()
                    .filter(r -> (c.equals("All") || r.category().equals(c)) && (r.title() + r.description()).toLowerCase().contains(k))
                    .map(r -> new Object[]{r.title(), r.category(), r.description(), r.contact()})
                    .collect(Collectors.toList());
            holder.removeAll();
            holder.add(Theme.scroll(Theme.table(new String[]{"Title", "Category", "Details", "Contact"}, rows, 160, 120, 340, 140)));
            holder.revalidate();
            holder.repaint();
        };
        query.addActionListener(e -> draw.run());
        category.addActionListener(e -> draw.run());
        draw.run();
        put(Theme.heading("Resource Directory"), Theme.fieldLabel("Search (press Enter)"), query,
                Theme.fieldLabel("Category"), category, holder);
    }
}

class SolutionsView extends View {
    SolutionsView(MainWindow w) {
        super(w);
        put(Theme.heading("Possible Solutions"),
                Theme.text("Practical ideas to try. They are suggestions, not guaranteed treatments."));
        int i = 0;
        for (Solution s : store().getSolutions()) {
            RoundPanel box = new RoundPanel(new BorderLayout(), Theme.PASTELS[i++ % Theme.PASTELS.length], null, 20);
            box.setBorder(new EmptyBorder(14, 18, 14, 18));
            JLabel head = new JLabel(s.category().toUpperCase() + "  |  " + s.title());
            head.setFont(Theme.BOLD);
            head.setForeground(Theme.GREEN_DARK);
            head.setBorder(new EmptyBorder(0, 0, 8, 0));
            JTextArea steps = Theme.text(s.steps().stream().map(x -> "\u2022  " + x).collect(Collectors.joining("\n")));
            box.add(head, BorderLayout.NORTH);
            box.add(steps, BorderLayout.CENTER);
            put(box);
        }
    }
}

class BookingView extends View {
    BookingView(MainWindow w) {
        super(w);
        List<CounselorInfo> counselors = store().getCounselors();
        JComboBox<String> counselor = Theme.combo(counselors.stream().map(CounselorInfo::name).toArray(String[]::new));
        JComboBox<String> date = Theme.combo(DataStore.nextWeekdays(10).toArray(new String[0]));
        JComboBox<String> time = Theme.combo(new String[0]);
        JTextField reason = Theme.field();
        Runnable updateSlots = () -> {
            time.removeAllItems();
            store().freeSlots(counselors.get(counselor.getSelectedIndex()).id(), (String) date.getSelectedItem()).forEach(time::addItem);
        };
        counselor.addActionListener(e -> updateSlots.run());
        date.addActionListener(e -> updateSlots.run());
        updateSlots.run();

        put(Theme.heading("Book an In-Person Consultation"),
                Theme.notice("Consultations are held in person at the TIP QC Guidance Office (Mon-Fri, 9 AM-4 PM). "
                        + "Keep the reason general; in an emergency call 1553 or 911."),
                Theme.fieldLabel("Counselor"), counselor,
                Theme.fieldLabel("Date"), date,
                Theme.fieldLabel("Available time"), time,
                Theme.fieldLabel("General reason"), reason,
                Theme.primary("Submit request", () -> {
                    String cid = counselors.get(counselor.getSelectedIndex()).id();
                    String d = (String) date.getSelectedItem();
                    String t = (String) time.getSelectedItem();
                    if (t == null) { window.message("No slots that day. Choose another date."); return; }
                    if (reason.getText().trim().length() < 5) { window.message("Please add a short general reason."); return; }
                    if (!store().freeSlots(cid, d).contains(t)) { window.message("That slot was just taken."); updateSlots.run(); return; }
                    store().addAppointment(new Appointment(user().getId(), cid, d, t, reason.getText().trim(), Status.PENDING));
                    window.message("Appointment Request Submitted");
                    window.open("My Appointments");
                }));
    }
}

/** Appointment table for students (own records) and staff (all records). */
class AppointmentsView extends View {
    private final List<Appointment> list;
    private final JTable table;

    AppointmentsView(MainWindow w, boolean staff) {
        super(w);
        list = store().appointmentsFor(user());
        List<Object[]> rows = list.stream().map(a -> new Object[]{
                a.getDate() + " " + a.getTime(),
                staff ? store().nameOf(a.getStudentId()) + " (" + store().courseOf(a.getStudentId()) + ")"
                        : store().counselorName(a.getCounselorId()),
                a.getReason(), a.getStatus()}).collect(Collectors.toList());
        table = Theme.table(new String[]{"When", staff ? "Student" : "Counselor", "Reason", "Status"}, rows, 140, 220, 220, 100);

        JPanel bar = new HugPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setOpaque(false);
        if (staff) {
            bar.add(Theme.primary("Confirm", () -> change(Status.CONFIRMED)));
            bar.add(Box.createHorizontalStrut(8));
            bar.add(Theme.primary("Complete", () -> change(Status.COMPLETED)));
            bar.add(Box.createHorizontalStrut(8));
            bar.add(Theme.primary("Reschedule", this::reschedule));
            bar.add(Box.createHorizontalStrut(8));
        }
        bar.add(Theme.secondary("Cancel", () -> change(Status.CANCELLED)));

        put(Theme.heading(staff ? "Appointments" : "My Appointments"),
                Theme.text("All consultations are in person at the Guidance Office. Select a row, then choose an action."),
                Theme.scroll(table), bar);
    }

    private Appointment selected() {
        int r = table.getSelectedRow();
        if (r < 0) { window.message("Select an appointment first."); return null; }
        return list.get(r);
    }

    private void change(Status s) {
        Appointment a = selected();
        if (a != null && window.confirm("Mark this appointment as " + s + "?")) {
            a.setStatus(s);
            window.refresh();
        }
    }

    private void reschedule() {
        Appointment a = selected();
        if (a == null) return;
        JComboBox<String> d = Theme.combo(DataStore.nextWeekdays(14).toArray(new String[0]));
        JComboBox<String> t = Theme.combo(DataStore.TIME_SLOTS);
        int ok = JOptionPane.showConfirmDialog(window, new Object[]{"New date", d, "New time", t},
                "Reschedule", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        String date = (String) d.getSelectedItem(), time = (String) t.getSelectedItem();
        if (!store().freeSlots(a.getCounselorId(), date).contains(time)) { window.message("That slot is taken. Pick another."); return; }
        a.reschedule(date, time);
        window.refresh();
    }
}

class AdminDashboardView extends View {
    AdminDashboardView(MainWindow w) {
        super(w);
        DataStore d = store();
        long students = d.getUsers().stream().filter(u -> u.getRole().equals("student")).count();
        long pending = d.getAppointments().stream().filter(a -> a.getStatus() == Status.PENDING).count();
        JPanel grid = new HugPanel(new GridLayout(2, 3, 14, 14));
        grid.setOpaque(false);
        grid.add(Theme.card("Students", String.valueOf(students), Theme.MINT));
        grid.add(Theme.card("Staff", String.valueOf(d.getUsers().size() - students), Theme.PEACH));
        grid.add(Theme.card("Assessments taken", String.valueOf(d.getResults().size()), Theme.SKY));
        grid.add(Theme.card("Check-ins", String.valueOf(d.getCheckIns().size()), Theme.LAVENDER));
        grid.add(Theme.card("Appointments", d.getAppointments().size() + " (pending: " + pending + ")", Theme.MINT));
        grid.add(Theme.card("Resources", String.valueOf(d.getResources().size()), Theme.PEACH));
        put(Theme.heading("Admin Dashboard"), grid, Theme.notice("Aggregate counts only, no identities."));
    }
}

class UsersView extends View {
    UsersView(MainWindow w) {
        super(w);
        List<User> users = store().getUsers();
        List<Object[]> rows = users.stream()
                .map(u -> new Object[]{u.getName(), u.getEmail(), u.getRole(), u.isActive() ? "Active" : "Deactivated"})
                .collect(Collectors.toList());
        JTable t = Theme.table(new String[]{"Name", "Email", "Role", "Status"}, rows, 180, 220, 100, 100);
        put(Theme.heading("Manage Users"), Theme.scroll(t), Theme.primary("Activate / Deactivate selected", () -> {
            int r = t.getSelectedRow();
            if (r < 0) { window.message("Select a user first."); return; }
            User u = users.get(r);
            if (u.getId().equals(user().getId())) { window.message("You cannot deactivate yourself."); return; }
            u.setActive(!u.isActive());
            window.refresh();
        }));
    }
}

/* =====================================================================
 *  WINDOWS
 * ===================================================================== */

/** Login screen: dark green brand panel on the left, white sign-in form on the right. */
class LoginPanel extends JPanel {
    private final MainWindow window;

    LoginPanel(MainWindow window) {
        super(new BorderLayout());
        this.window = window;
        add(brandPanel(), BorderLayout.WEST);
        add(formPanel(), BorderLayout.CENTER);
    }

    private JPanel brandPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.GREEN_DARK);
        p.setBorder(new EmptyBorder(48, 44, 48, 44));
        p.setPreferredSize(new Dimension(420, 0));

        JLabel name = new JLabel("Kalma");
        name.setFont(new Font("SansSerif", Font.BOLD, 44));
        name.setForeground(Theme.WHITE);
        JPanel bar = new JPanel();
        bar.setBackground(new Color(0x8CC5A0));
        bar.setMaximumSize(new Dimension(70, 5));
        bar.setPreferredSize(new Dimension(70, 5));

        p.add(Box.createVerticalGlue());
        for (Component c : new Component[]{name, Box.createVerticalStrut(10), bar, Box.createVerticalStrut(18),
                Theme.wrapLabel("Peer-Support Wellness Companion", 300, Theme.WHITE, 18f, true), Box.createVerticalStrut(12),
                Theme.wrapLabel("Technological Institute of the Philippines, Quezon City", 300, Theme.MINT, 13f, false),
                Box.createVerticalStrut(28),
                Theme.wrapLabel("Supports SDG 3: Good Health and Well-Being", 300, new Color(0x8CC5A0), 12f, true)}) {
            ((JComponent) c).setAlignmentX(0f);
            p.add(c);
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel formPanel() {
        JTextField email = Theme.field();
        JPasswordField pw = Theme.passwordField();
        Runnable login = () -> {
            Optional<User> found = DataStore.getInstance().findByEmail(email.getText().trim());
            if (found.isEmpty() || !found.get().passwordMatches(new String(pw.getPassword()))) {
                window.message("Email or password is incorrect.");
                return;
            }
            if (!found.get().isActive()) { window.message("This account is deactivated. Contact the Admin."); return; }
            window.enter(found.get());
        };
        pw.addActionListener(e -> login.run());

        JButton loginBtn = Theme.primary("Log in", login);
        JButton registerBtn = Theme.secondary("Create student account", this::register);
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        registerBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(Theme.WHITE);
        Component[] items = {
                Theme.heading("Sign in"), Box.createVerticalStrut(14),
                Theme.fieldLabel("School email"), Box.createVerticalStrut(4), email, Box.createVerticalStrut(12),
                Theme.fieldLabel("Password"), Box.createVerticalStrut(4), pw, Box.createVerticalStrut(18),
                loginBtn, Box.createVerticalStrut(8), registerBtn, Box.createVerticalStrut(22),
                Theme.wrapLabel("Demo accounts: student@tip.edu.ph / student123, guidance@tip.edu.ph / guidance123, "
                        + "admin@tip.edu.ph / admin123", 340, Theme.MUTED, 11f, false),
                Box.createVerticalStrut(10),
                Theme.wrapLabel(Kalma.NOTE, 340, Theme.MUTED, 11f, false)};
        for (Component c : items) { ((JComponent) c).setAlignmentX(0f); inner.add(c); }
        inner.setPreferredSize(new Dimension(340, inner.getPreferredSize().height));

        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.WHITE);
        outer.add(inner);
        return outer;
    }

    private void register() {
        JTextField name = Theme.field(), email = Theme.field(), course = Theme.field();
        JPasswordField p1 = Theme.passwordField(), p2 = Theme.passwordField();
        Object[] fields = {"Full name", name, "School email", email, "Course and year", course,
                "Password (8+ characters, with a number)", p1, "Confirm password", p2};
        if (JOptionPane.showConfirmDialog(window, fields, "Create student account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String pw = new String(p1.getPassword());
        String em = email.getText().trim().toLowerCase();
        DataStore store = DataStore.getInstance();
        if (name.getText().isBlank() || course.getText().isBlank()) window.message("Name and course are required.");
        else if (!em.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) window.message("Enter a valid email address.");
        else if (store.findByEmail(em).isPresent()) window.message("That email is already registered.");
        else if (pw.length() < 8 || !pw.matches(".*\\d.*")) window.message("Password needs 8+ characters and at least one number.");
        else if (!pw.equals(new String(p2.getPassword()))) window.message("Passwords do not match.");
        else {
            Student s = new Student("u" + UUID.randomUUID().toString().substring(0, 6), name.getText().trim(), em, pw, course.getText().trim());
            store.addUser(s);
            window.enter(s);
        }
    }
}

/** Main application window: sage sidebar, white top bar and a soft green content area. */
class MainWindow extends JFrame {
    private User user;
    private JPanel body;
    private JLabel pageTitle;
    private String currentKey;
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();

    MainWindow() {
        super("Kalma - Peer-Support Wellness Companion");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 760);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
    }

    User getUser() { return user; }
    void message(String s) { JOptionPane.showMessageDialog(this, s, "Kalma", JOptionPane.PLAIN_MESSAGE); }
    boolean confirm(String s) {
        return JOptionPane.showConfirmDialog(this, s, "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    void showLogin() {
        user = null;
        setContentPane(new LoginPanel(this));
        revalidate();
        repaint();
        setVisible(true);
    }

    void enter(User u) {
        user = u;
        body = new JPanel(new BorderLayout());
        body.setBackground(Theme.BG);
        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.BG);
        right.add(buildTopBar(), BorderLayout.NORTH);
        right.add(body, BorderLayout.CENTER);
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);
        open(u.getMenu()[0]);
        revalidate();
        repaint();
    }

    void open(String key) {
        currentKey = key;
        navButtons.forEach((k, b) -> Theme.setNavSelected(b, k.equals(key)));
        pageTitle.setText(key);
        JScrollPane sp = new JScrollPane(createView(key));
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        body.removeAll();
        body.add(sp, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    void refresh() { open(currentKey); }

    private View createView(String key) {
        return switch (key) {
            case "Home" -> new DashboardView(this);
            case "Assessment" -> new AssessmentView(this);
            case "Daily Check-In" -> new CheckInView(this);
            case "Trends" -> new TrendsView(this);
            case "Resources" -> new ResourcesView(this);
            case "Solutions" -> new SolutionsView(this);
            case "Book Consultation" -> new BookingView(this);
            case "My Appointments" -> new AppointmentsView(this, false);
            case "Appointments" -> new AppointmentsView(this, true);
            case "Dashboard" -> new AdminDashboardView(this);
            case "Users" -> new UsersView(this);
            default -> throw new IllegalArgumentException("Unknown view: " + key);
        };
    }

    /** White header: current page title on the left, user avatar and name on the right. */
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.WHITE);
        bar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Theme.GRID), new EmptyBorder(10, 28, 10, 24)));

        pageTitle = new JLabel(" ");
        pageTitle.setFont(Theme.BASE.deriveFont(Font.BOLD, 16f));
        pageTitle.setForeground(Theme.GREEN_DARK);

        JLabel nm = new JLabel(user.getName());
        nm.setFont(Theme.BOLD);
        nm.setForeground(Theme.INK);
        JLabel rl = new JLabel(user.getRole().substring(0, 1).toUpperCase() + user.getRole().substring(1));
        rl.setFont(Theme.BASE.deriveFont(11f));
        rl.setForeground(Theme.MUTED);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(nm);
        names.add(rl);

        JPanel who = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        who.setOpaque(false);
        who.add(new Avatar(user.getInitials()));
        who.add(names);

        bar.add(pageTitle, BorderLayout.WEST);
        bar.add(who, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildSidebar() {
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(Theme.SAGE);
        nav.setBorder(new EmptyBorder(22, 14, 20, 14));
        nav.setPreferredSize(new Dimension(230, 0));

        JPanel logo = new JPanel();
        logo.setOpaque(false);
        logo.setLayout(new BoxLayout(logo, BoxLayout.X_AXIS));
        JLabel brand = new JLabel("Kalma");
        brand.setFont(new Font("SansSerif", Font.BOLD, 26));
        brand.setForeground(Theme.GREEN_DARK);
        logo.add(new Dot(Theme.GREEN_DARK, 24));
        logo.add(Box.createHorizontalStrut(8));
        logo.add(brand);
        logo.setAlignmentX(0f);

        JLabel tagline = new JLabel("Better Minds. Brighter Tomorrows.");
        tagline.setFont(Theme.BASE.deriveFont(10f));
        tagline.setForeground(Theme.MUTED);
        tagline.setAlignmentX(0f);

        nav.add(logo);
        nav.add(Box.createVerticalStrut(2));
        nav.add(tagline);
        nav.add(Box.createVerticalStrut(20));

        navButtons.clear();
        for (String key : user.getMenu()) {
            JButton b = Theme.navButton(key, () -> open(key));
            navButtons.put(key, b);
            nav.add(b);
            nav.add(Box.createVerticalStrut(4));
        }
        nav.add(Box.createVerticalGlue());

        JButton logout = Theme.secondary("Log out", this::showLogin);
        logout.setAlignmentX(0f);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        nav.add(logout);
        return nav;
    }
}