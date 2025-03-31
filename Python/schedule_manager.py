import tkinter as tk
from tkinter import messagebox
import json
import random
from collections import defaultdict

# --- Constants ---
DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
SHIFTS = ["morning", "afternoon", "evening"]
MAX_DAYS = 5  # Maximum workdays per week

# Global list to hold employee data
# Each employee is stored as:
# { "name": <str>, "preferences": { day: {"primary": <str>, "secondary": <str>} } }
employee_data = []

# --- Helper Function to Assign Employee to a Shift ---
def assign_employee(employee_name, day, shift, schedule, employee_workdays):
    """Assign an employee to a given shift on a day if possible.
    Returns True if assignment is done, else False."""
    # Check if the shift is not full (i.e., fewer than 2 employees)
    if len(schedule[day][shift]) < 2:
        # Also, check if employee hasn't already worked on this day
        if day not in employee_workdays[employee_name]:
            schedule[day][shift].append(employee_name)
            employee_workdays[employee_name].add(day)
            return True
    return False

# --- Function to Add Employee Data from GUI ---
def add_employee():
    name = name_entry.get().strip()
    if not name:
        messagebox.showerror("Input Error", "Please enter an employee name.")
        return

    # Gather preferences for each day (both primary and secondary)
    preferences = {}
    for day in DAYS:
        primary = primary_vars[day].get()
        secondary = secondary_vars[day].get()
        # Save preferences only if at least one shift is chosen
        if primary != "none" or secondary != "none":
            preferences[day] = {"primary": primary, "secondary": secondary}
    # Save employee data
    employee_data.append({"name": name, "preferences": preferences})
    messagebox.showinfo("Employee Added", f"Added employee: {name}")
    name_entry.delete(0, tk.END)
    # Reset dropdowns for each day
    for day in DAYS:
        primary_vars[day].set("none")
        secondary_vars[day].set("none")

# --- Main Scheduling Function ---
def generate_schedule():
    # Initialize schedule: day -> shift -> list of employee names
    schedule = {day: {shift: [] for shift in SHIFTS} for day in DAYS}
    # Track days already assigned per employee
    employee_workdays = {emp["name"]: set() for emp in employee_data}
    # To keep track of employees not assigned on a given day (for conflict resolution)
    unassigned = defaultdict(list)  # day -> list of employee names

    # --- First Pass: Try to assign using employee preferences on the same day ---
    for day in DAYS:
        for emp in employee_data:
            name = emp["name"]
            # Skip if employee already reached max workdays
            if len(employee_workdays[name]) >= MAX_DAYS:
                continue
            # If the employee is not available (i.e. did not specify any preference for the day)
            if day not in emp["preferences"]:
                continue
            # If already assigned on this day, skip
            if day in employee_workdays[name]:
                continue

            prefs = emp["preferences"][day]
            assigned = False

            # Try primary preference if not "none"
            if prefs["primary"] != "none":
                assigned = assign_employee(name, day, prefs["primary"], schedule, employee_workdays)
            # If primary failed, try secondary preference if not "none"
            if not assigned and prefs["secondary"] != "none":
                assigned = assign_employee(name, day, prefs["secondary"], schedule, employee_workdays)
            # If still not assigned, try any available shift on the same day
            if not assigned:
                for shift in SHIFTS:
                    if assign_employee(name, day, shift, schedule, employee_workdays):
                        assigned = True
                        break
            # If assignment still fails, mark the employee as unassigned for that day
            if not assigned:
                unassigned[day].append(name)

    # --- Second Pass: Resolve Conflicts by Trying the Next Day ---
    # For employees unassigned on a day, try to assign them on the next day if possible.
    for i, day in enumerate(DAYS):
        if day in unassigned:
            next_day = DAYS[i+1] if i+1 < len(DAYS) else None
            still_unassigned = []
            for name in unassigned[day]:
                # Skip if already reached max workdays
                if len(employee_workdays[name]) >= MAX_DAYS:
                    continue
                assigned = False
                # Try next day if available
                if next_day:
                    # Check if employee specified preferences for next day
                    if next_day in get_employee_preferences(name):
                        prefs = get_employee_preferences(name)[next_day]
                        if prefs["primary"] != "none":
                            assigned = assign_employee(name, next_day, prefs["primary"], schedule, employee_workdays)
                        if not assigned and prefs["secondary"] != "none":
                            assigned = assign_employee(name, next_day, prefs["secondary"], schedule, employee_workdays)
                    # If still not assigned, try any shift on next day
                    if not assigned:
                        for shift in SHIFTS:
                            if assign_employee(name, next_day, shift, schedule, employee_workdays):
                                assigned = True
                                break
                # If still not assigned after next day attempt, keep the employee unassigned (could be left out)
                if not assigned:
                    still_unassigned.append(name)
            # Update unassigned list (for now we simply note them; further logic could chain to later days)
            unassigned[day] = still_unassigned

    # --- Third Pass: Ensure Each Shift Has at Least 2 Employees ---
    # For each day and each shift, if fewer than 2 employees are assigned, randomly assign additional employees.
    for day in DAYS:
        for shift in SHIFTS:
            while len(schedule[day][shift]) < 2:
                # Build list of candidate employees: must not work on that day and not exceeded MAX_DAYS
                candidates = [emp["name"] for emp in employee_data
                              if day not in employee_workdays[emp["name"]] and len(employee_workdays[emp["name"]]) < MAX_DAYS]
                if not candidates:
                    break  # No candidate available; exit loop
                chosen = random.choice(candidates)
                # Assign chosen candidate to this shift on this day
                schedule[day][shift].append(chosen)
                employee_workdays[chosen].add(day)

    # --- Output the Schedule ---
    # Save the schedule to a JSON file
    with open("weekly_schedule_output.json", "w") as f:
        json.dump(schedule, f, indent=4)

    # Display schedule in the text widget in a readable format
    output_text.delete("1.0", tk.END)
    output_text.insert(tk.END, "Final Weekly Schedule:\n")
    output_text.insert(tk.END, "-----------------------\n")
    for day in DAYS:
        output_text.insert(tk.END, f"{day}:\n")
        for shift in SHIFTS:
            assigned_emp = schedule[day][shift]
            output_text.insert(tk.END, f"  {shift.capitalize()}: {', '.join(assigned_emp)}\n")
        output_text.insert(tk.END, "\n")
    messagebox.showinfo("Schedule Generated", "The weekly schedule has been generated and saved to weekly_schedule_output.json")

# --- Helper Function to Retrieve an Employee's Preferences by Name ---
def get_employee_preferences(name):
    for emp in employee_data:
        if emp["name"] == name:
            return emp["preferences"]
    return {}

# --- GUI Layout using Tkinter ---
app = tk.Tk()
app.title("Employee Schedule Manager")
app.geometry("700x800")

# Title Label
tk.Label(app, text="Employee Schedule Manager", font=("Helvetica", 16, "bold")).grid(row=0, column=0, columnspan=4, pady=10)

# Employee Name Input
tk.Label(app, text="Employee Name:").grid(row=1, column=0, padx=10, pady=5, sticky="e")
name_entry = tk.Entry(app, width=30)
name_entry.grid(row=1, column=1, padx=10, pady=5)

# For each day, we create two dropdowns: one for primary and one for secondary shift preference.
primary_vars = {}
secondary_vars = {}
start_row = 2
for i, day in enumerate(DAYS):
    # Label for the day
    tk.Label(app, text=day, font=("Helvetica", 10, "bold")).grid(row=start_row+i, column=0, padx=10, pady=2, sticky="e")
    # Primary preference dropdown
    var_primary = tk.StringVar(value="none")
    primary_vars[day] = var_primary
    tk.Label(app, text="Primary:").grid(row=start_row+i, column=1, sticky="w")
    tk.OptionMenu(app, var_primary, "none", "morning", "afternoon", "evening").grid(row=start_row+i, column=2, sticky="w", padx=5)
    # Secondary preference dropdown
    var_secondary = tk.StringVar(value="none")
    secondary_vars[day] = var_secondary
    tk.Label(app, text="Secondary:").grid(row=start_row+i, column=3, sticky="w")
    tk.OptionMenu(app, var_secondary, "none", "morning", "afternoon", "evening").grid(row=start_row+i, column=4, sticky="w", padx=5)

# Buttons for adding employee and generating schedule
button_row = start_row + len(DAYS) + 1
tk.Button(app, text="Add Employee", command=add_employee, width=20, bg="#d1e7dd").grid(row=button_row, column=1, pady=10)
tk.Button(app, text="Generate Schedule", command=generate_schedule, width=20, bg="#cfe2ff").grid(row=button_row, column=2, pady=10)

# Output Text Area to display final schedule
output_text = tk.Text(app, height=20, width=80)
output_text.grid(row=button_row+1, column=0, columnspan=5, padx=10, pady=10)

# Run the application
app.mainloop()
