import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class EmployeeSchedulerApp {
    private JFrame frame;
    private JTextField nameField;
    private JTextArea outputArea;

    // employeePreferences holds: {employeeName -> {day -> [primaryShift,
    // secondaryShift]}}
    private final Map<String, Map<String, String[]>> employeePreferences = new LinkedHashMap<>();

    // The days and shift options
    private final String[] days = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };
    private final String[] shifts = { "none", "morning", "afternoon", "evening" };

    // For each day, we’ll store two combo boxes: one for primary, one for secondary
    private final Map<String, JComboBox<String>[]> dayShiftBoxes = new LinkedHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EmployeeSchedulerApp().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Employee Schedule Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 750);
        frame.setLayout(new BorderLayout());

        // Top panel for inputs
        JPanel inputPanel = new JPanel(new GridLayout(10, 4));

        nameField = new JTextField(20);
        inputPanel.add(new JLabel("Employee Name:"));
        inputPanel.add(nameField);
        // Filler labels to keep layout consistent
        inputPanel.add(new JLabel());
        inputPanel.add(new JLabel());

        // For each day, add two combo boxes for primary & secondary
        for (String day : days) {
            inputPanel.add(new JLabel(day));

            JComboBox<String> primaryBox = new JComboBox<>(shifts);
            JComboBox<String> secondaryBox = new JComboBox<>(shifts);

            // Save them so we can retrieve their values
            dayShiftBoxes.put(day, new JComboBox[] { primaryBox, secondaryBox });

            inputPanel.add(new JLabel("Primary:"));
            inputPanel.add(primaryBox);
            inputPanel.add(new JLabel("Secondary:"));
            inputPanel.add(secondaryBox);
        }

        JButton addButton = new JButton("Add Employee");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addEmployee();
            }
        });

        JButton generateButton = new JButton("Generate Schedule");
        generateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateSchedule();
            }
        });

        inputPanel.add(addButton);
        inputPanel.add(generateButton);

        // Center panel: output area
        outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    /**
     * Collect the employee name and day-wise preferences, store in
     * employeePreferences map.
     */
    private void addEmployee() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter an employee name.");
            return;
        }

        // Build a map of day -> [primaryShift, secondaryShift]
        Map<String, String[]> prefs = new LinkedHashMap<>();
        for (String day : days) {
            JComboBox<String>[] boxes = dayShiftBoxes.get(day);
            String primary = (String) boxes[0].getSelectedItem();
            String secondary = (String) boxes[1].getSelectedItem();
            prefs.put(day, new String[] { primary, secondary });
        }

        employeePreferences.put(name, prefs);

        // Reset the input fields
        nameField.setText("");
        for (JComboBox<String>[] boxes : dayShiftBoxes.values()) {
            boxes[0].setSelectedIndex(0);
            boxes[1].setSelectedIndex(0);
        }

        JOptionPane.showMessageDialog(frame, "Employee added: " + name);
    }

    /**
     * Generate the schedule:
     * 1) Assign employees based on preferences.
     * 2) Ensure each shift has at least 2 employees.
     * 3) No employee works more than 5 days, nor more than 1 shift/day.
     */
    private void generateSchedule() {
        // schedule: day -> (shift -> listOfEmployeeNames)
        Map<String, Map<String, List<String>>> schedule = new LinkedHashMap<>();
        // Track how many days each employee has worked (max 5)
        Map<String, Integer> workCount = new LinkedHashMap<>();
        // Track days each employee has already worked on (1 shift max per day)
        Map<String, List<String>> daysWorked = new LinkedHashMap<>();

        for (String emp : employeePreferences.keySet()) {
            workCount.put(emp, 0);
            daysWorked.put(emp, new ArrayList<>());
        }

        // Initialize schedule
        for (String day : days) {
            schedule.put(day, new LinkedHashMap<>());
            for (String shift : new String[] { "morning", "afternoon", "evening" }) {
                schedule.get(day).put(shift, new ArrayList<>());
            }
        }

        // 1) First pass: assign based on preferences
        for (String emp : employeePreferences.keySet()) {
            Map<String, String[]> prefs = employeePreferences.get(emp);

            for (String day : days) {
                if (workCount.get(emp) >= 5) {
                    break; // Employee already at max 5 days
                }
                // If already worked that day, skip
                if (daysWorked.get(emp).contains(day)) {
                    continue;
                }

                String primary = prefs.get(day)[0];
                String secondary = prefs.get(day)[1];
                boolean assigned = false;

                // Try primary shift (if not "none")
                if (!primary.equals("none") && canAssign(schedule, emp, day, primary, workCount, daysWorked)) {
                    schedule.get(day).get(primary).add(emp);
                    assigned = true;
                }
                // If not assigned, try secondary
                if (!assigned && !secondary.equals("none")
                        && canAssign(schedule, emp, day, secondary, workCount, daysWorked)) {
                    schedule.get(day).get(secondary).add(emp);
                    assigned = true;
                }
                // If still not assigned, try any shift
                if (!assigned) {
                    for (String shift : new String[] { "morning", "afternoon", "evening" }) {
                        if (canAssign(schedule, emp, day, shift, workCount, daysWorked)) {
                            schedule.get(day).get(shift).add(emp);
                            break;
                        }
                    }
                }
            }
        }

        // 2) Ensure each shift has at least 2 employees
        Random rand = new Random();
        for (String day : days) {
            for (String shift : new String[] { "morning", "afternoon", "evening" }) {
                while (schedule.get(day).get(shift).size() < 2) {
                    // Build a list of candidates who haven't worked 5 days or that day
                    List<String> candidates = new ArrayList<>();
                    for (String emp : employeePreferences.keySet()) {
                        if (workCount.get(emp) < 5 && !daysWorked.get(emp).contains(day)) {
                            candidates.add(emp);
                        }
                    }
                    if (candidates.isEmpty()) {
                        break; // No more candidates
                    }
                    String chosen = candidates.get(rand.nextInt(candidates.size()));
                    schedule.get(day).get(shift).add(chosen);
                    workCount.put(chosen, workCount.get(chosen) + 1);
                    daysWorked.get(chosen).add(day);
                }
            }
        }

        // 3) Display the schedule in the GUI
        displaySchedule(schedule);

        // 4) Save to JSON file
        saveScheduleToJson(schedule);
    }

    /**
     * Checks if we can assign emp to (day, shift)
     * under the rules:
     * - max 5 days
     * - only 1 shift per day
     * - max 2 employees per shift
     */
    private boolean canAssign(
            Map<String, Map<String, List<String>>> schedule,
            String emp,
            String day,
            String shift,
            Map<String, Integer> workCount,
            Map<String, List<String>> daysWorked) {
        if (schedule.get(day).get(shift).size() < 2) {
            if (!daysWorked.get(emp).contains(day)) {
                if (workCount.get(emp) < 5) {
                    // Assign
                    schedule.get(day).get(shift).add(emp);
                    workCount.put(emp, workCount.get(emp) + 1);
                    daysWorked.get(emp).add(day);
                    return true;
                }
            }
        }
        return false;
    }

    private void displaySchedule(Map<String, Map<String, List<String>>> schedule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generated Weekly Schedule:\n\n");
        for (String day : days) {
            sb.append(day).append(":\n");
            for (String shift : new String[] { "morning", "afternoon", "evening" }) {
                List<String> assigned = schedule.get(day).get(shift);
                sb.append("  ").append(shift).append(": ")
                        .append(String.join(", ", assigned)).append("\n");
            }
            sb.append("\n");
        }
        outputArea.setText(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private void saveScheduleToJson(Map<String, Map<String, List<String>>> schedule) {
        // Build JSON object: { day: { shift: [employees...] } }
        JSONObject root = new JSONObject();
        for (String day : days) {
            JSONObject dayObj = new JSONObject();
            for (String shift : new String[] { "morning", "afternoon", "evening" }) {
                JSONArray arr = new JSONArray();
                for (String emp : schedule.get(day).get(shift)) {
                    arr.add(emp);
                }
                dayObj.put(shift, arr);
            }
            root.put(day, dayObj);
        }

        try (FileWriter writer = new FileWriter("weekly_schedule_output.json")) {
            writer.write(root.toJSONString());
            JOptionPane.showMessageDialog(frame, "Schedule saved to weekly_schedule_output.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
