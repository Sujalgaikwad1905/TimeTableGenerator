import java.util.*;

public class TimetableGenerator {
    // Represents a scheduled session (lecture, lab, tutorial, or break)
    static class Session {
        String division;  // e.g., "A" or "A1" for sub-batch
        String subject;
        String type;      // "Lecture", "Lab", "Tutorial", or "Break"
        int duration;     // in hours
        String faculty;
        String room;
        int day, startHour;

        // Tracks total hours scheduled per division per day
        static int[][] divisionHours = new int[4][5];

        Session(String division, String subject, String type, int duration) {
            this.division = division;
            this.subject = subject;
            this.type = type;
            this.duration = duration;
            this.faculty = null;
            this.room = null;
            this.day = -1;
            this.startHour = -1;
        }

        @Override
        public String toString() {
            if (type.equals("Break")) {
                return dayName[day] + " " + formatHour(startHour) + "-" + formatHour(startHour + duration) +
                        " BREAK for Div " + division;
            }
            return dayName[day] + " " + formatHour(startHour) + "-" +
                    formatHour(startHour + duration) + " " + subject +
                    " " + type + " @ " + room + " by " + faculty +
                    " on Div " + division;
        }
    }

    static String[] dayName = {"Monday","Tuesday","Wednesday","Thursday","Friday"};

    static Map<String,List<String>> facultyBySubject = new HashMap<>();
    static String[] lectureRooms = {"Room101","Room104","Room201","Room204"};
    static String[] tutorialRooms = {"Room214","Room314","Room104"};
    static String[] labRooms = {"105Lab","106Lab","307Lab"};

    static boolean[][][] divOccupied = new boolean[4][5][10];
    static boolean[][][] roomOccupied;
    static boolean[][][] facultyOccupied;
    static String[] allRooms;
    static List<String> allFaculties = new ArrayList<>();
    static List<Session> events = new ArrayList<>();
    static boolean solved = false;

    public static void main(String[] args) {
        // Set up faculties for each subject
        facultyBySubject.put("CN", Arrays.asList("NNS","VAMI"));
        facultyBySubject.put("DSML", Arrays.asList("PDM","NNW"));
        facultyBySubject.put("SEPM", Arrays.asList("MPM","YYD"));
        facultyBySubject.put("SMSS", Arrays.asList("LAB","VKK"));
        facultyBySubject.put("AI", Arrays.asList("LAB"));
        facultyBySubject.put("BIDA", Arrays.asList("NK"));

        // Merge all room lists, removing duplicates
        LinkedHashSet<String> roomsSet = new LinkedHashSet<>();
        for(String r: lectureRooms) roomsSet.add(r);
        for(String r: tutorialRooms) roomsSet.add(r);
        for(String r: labRooms) roomsSet.add(r);
        allRooms = roomsSet.toArray(new String[0]);
        roomOccupied = new boolean[5][10][allRooms.length];

        // Compile all faculty names into a single list
        for(List<String> fs : facultyBySubject.values()) {
            for(String f : fs) {
                if (!allFaculties.contains(f)) {
                    allFaculties.add(f);
                }
            }
        }
        facultyOccupied = new boolean[5][10][allFaculties.size()];

        // Define subject allocations per division
        Map<String,List<String>> subjectsByDiv = new HashMap<>();
        subjectsByDiv.put("A", Arrays.asList("CN","DSML","SEPM","SMSS"));
        subjectsByDiv.put("B", Arrays.asList("CN","DSML","SEPM","SMSS"));
        subjectsByDiv.put("C", Arrays.asList("CN","DSML","SMSS","SEPM"));
        subjectsByDiv.put("D", Arrays.asList("CN","DSML","SEPM","SMSS"));

        // Set required session counts
        Map<String,Integer> lecCount = Map.of("CN", 2, "DSML", 2, "SEPM", 2, "SMSS", 2);
        Map<String,Integer> labCount = Map.of("CN", 1, "DSML", 1, "SEPM", 1, "SMSS", 0);
        Map<String,Integer> tutCount = Map.of("CN", 0, "DSML", 0, "SEPM", 0, "SMSS", 1);

        // Add fixed AI and BIDA lectures for A, B, C divisions on Tuesday
        for(String div : Arrays.asList("A","B","C")) {
            Session bida = new Session(div, "BIDA", "Lecture", 2);
            bida.day = 1; bida.startHour = 9;
            bida.room = "Room101"; bida.faculty = "NK";
            markOccupied(bida);
            events.add(bida);

            Session ai = new Session(div, "AI", "Lecture", 2);
            ai.day = 1; ai.startHour = 12;
            ai.room = "Room104"; ai.faculty = "LAB";
            markOccupied(ai);
            events.add(ai);
        }

        // Add placeholder break sessions (one per division per day)
        String[] divs = {"A","B","C","D"};
        for(String div : divs) {
            for(int day=0; day<5; day++) {
                Session br = new Session(div, "BREAK", "Break", 1);
                events.add(br);
            }
        }

        // Add lecture, lab, and tutorial sessions as per subject requirements
        for(String div : divs) {
            for(String subj : subjectsByDiv.get(div)) {
                for(int i=0; i<lecCount.get(subj); i++) {
                    events.add(new Session(div, subj, "Lecture", 1));
                }
                if(labCount.get(subj) > 0) {
                    for(String sb : Arrays.asList("1","2","3")) {
                        events.add(new Session(div + sb, subj, "Lab", 2));
                    }
                }
                if(tutCount.get(subj) > 0) {
                    for(String sb : Arrays.asList("1","2","3")) {
                        events.add(new Session(div + sb, subj, "Tutorial", 1));
                    }
                }
            }
        }

        // Sort sessions so breaks and labs get priority during scheduling
        events.sort(Comparator.comparingInt((Session s) ->
                s.type.equals("Break") ? 0 :
                        s.type.equals("Lab") ? 1 :
                                s.type.equals("Tutorial") ? 2 : 3
        ));

        // Begin backtracking to generate the timetable
        backtrack(0);

        // Output the result
        if(solved) {
            System.out.println("\nTimetable Generated:\n");
            events.sort(Comparator.comparing((Session e) -> e.day).thenComparing(e -> e.startHour));
//            for(Session e : events) {
////                System.out.println(e.toString());
//            }
            print2DTimetables();
        } else {
            System.out.println("No valid timetable found.");
        }
    }

    // Main backtracking function to schedule sessions
    static void backtrack(int idx) {
        if (idx == events.size()) {
            if (!checkBreakDistribution()) return;
            if (!usesAllDays()) return;
            solved = true;
            return;
        }

        if(solved) return;

        Session ev = events.get(idx);
        if(ev.day != -1) {
            backtrack(idx+1);
            return;
        }

        if(ev.type.equals("Break")) {
            String div = ev.division;
            int divIdx = getDivisionIndex(div);
            for(int day=0; day<5; day++) {
                boolean hasBreak = false;
                for(Session e : events) {
                    if(e.type.equals("Break") && e.division.equals(div) && e.day == day) {
                        hasBreak = true;
                        break;
                    }
                }
                if(hasBreak) continue;

                if(day == 1 && (div.equals("A")||div.equals("B")||div.equals("C"))) {
                    int h = 11;
                    if(isFree(divIdx, day, h)) {
                        ev.day = day; ev.startHour = h;
                        markOccupied(ev);
                        backtrack(idx+1);
                        if(solved) return;
                        unmarkOccupied(ev);
                        ev.day = -1;
                    }
                    continue;
                }
                for(int h = 11; h <= 13; h++) {
                    if(!isFree(divIdx, day, h)) continue;
                    ev.day = day; ev.startHour = h;
                    markOccupied(ev);
                    backtrack(idx+1);
                    if(solved) return;
                    unmarkOccupied(ev);
                    ev.day = -1;
                }
            }

        } else {
            List<String> facList = facultyBySubject.getOrDefault(ev.subject, Collections.emptyList());
            String[] rooms = ev.type.equals("Lecture") ? lectureRooms :
                    ev.type.equals("Tutorial") ? tutorialRooms : labRooms;

            String mainDiv = ev.division.substring(0, 1);
            int divIdx = getDivisionIndex(mainDiv);

            for(int day=0; day<5; day++) {
                if (Session.divisionHours[divIdx][day] + ev.duration > 8) {
                    continue;
                }

                for(int h=8; h<=17; h++) {
                    if(h + ev.duration > 18) break;

                    boolean freeDiv = true;
                    for(int hh=h; hh<h+ev.duration; hh++) {
                        if(divOccupied[divIdx][day][hh-8]) { freeDiv = false; break; }
                    }
                    if(!freeDiv) continue;

                    for(String room : rooms) {
                        int roomIdx = Arrays.asList(allRooms).indexOf(room);
                        boolean freeRoom = true;
                        for(int hh=h; hh<h+ev.duration; hh++) {
                            if(roomOccupied[day][hh-8][roomIdx]) { freeRoom = false; break; }
                        }
                        if(!freeRoom) continue;

                        for(String fac : facList) {
                            int facIdx = allFaculties.indexOf(fac);
                            boolean freeFac = true;
                            for(int hh=h; hh<h+ev.duration; hh++) {
                                if(facIdx>=0 && facultyOccupied[day][hh-8][facIdx]) { freeFac = false; break; }
                            }
                            if(!freeFac) continue;

                            ev.day = day; ev.startHour = h;
                            ev.room = room; ev.faculty = fac;
                            markOccupied(ev);
                            backtrack(idx+1);
                            if(solved) return;
                            unmarkOccupied(ev);
                            ev.day = -1; ev.room = null; ev.faculty = null;
                        }
                    }
                }
            }
        }
    }

    static boolean isFree(int divIdx, int day, int hour) {
        return !divOccupied[divIdx][day][hour-8];
    }

    static void markOccupied(Session ev) {
        String mainDiv = ev.division.substring(0, 1);
        int divIdx = getDivisionIndex(mainDiv);

        if (ev.type.equals("Break")) {
            divOccupied[divIdx][ev.day][ev.startHour - 8] = true;
            Session.divisionHours[divIdx][ev.day] += ev.duration;
            return;
        }

        Session.divisionHours[divIdx][ev.day] += ev.duration;

        for (int h = ev.startHour; h < ev.startHour + ev.duration; h++) {
            divOccupied[divIdx][ev.day][h - 8] = true;
        }

        int roomIdx = Arrays.asList(allRooms).indexOf(ev.room);
        if (roomIdx >= 0) {
            for (int h = ev.startHour; h < ev.startHour + ev.duration; h++) {
                roomOccupied[ev.day][h - 8][roomIdx] = true;
            }
        }

        int facIdx = allFaculties.indexOf(ev.faculty);
        if (facIdx >= 0) {
            for (int h = ev.startHour; h < ev.startHour + ev.duration; h++) {
                facultyOccupied[ev.day][h - 8][facIdx] = true;
            }
        }
    }

    static void unmarkOccupied(Session ev) {
        String mainDiv = ev.division.substring(0, 1);
        int divIdx = getDivisionIndex(mainDiv);

        if(ev.type.equals("Break")) {
            divOccupied[divIdx][ev.day][ev.startHour-8] = false;
            Session.divisionHours[divIdx][ev.day] -= ev.duration;
            return;
        }

        Session.divisionHours[divIdx][ev.day] -= ev.duration;

        for(int h=ev.startHour; h<ev.startHour+ev.duration; h++) {
            divOccupied[divIdx][ev.day][h-8] = false;
        }
        int roomIdx = Arrays.asList(allRooms).indexOf(ev.room);
        if(roomIdx >= 0) {
            for(int h=ev.startHour; h<ev.startHour+ev.duration; h++) {
                roomOccupied[ev.day][h-8][roomIdx] = false;
            }
        }
        int facIdx = allFaculties.indexOf(ev.faculty);
        if(facIdx >= 0) {
            for(int h=ev.startHour; h<ev.startHour+ev.duration; h++) {
                facultyOccupied[ev.day][h-8][facIdx] = false;
            }
        }
    }

    static boolean checkBreakDistribution() {
        return true;
    }

    static String formatHour(int h) {
        return String.format("%02d:00", h);
    }

    static boolean usesAllDays() {
        Map<String, Set<Integer>> usedDays = new HashMap<>();
        for (Session s : events) {
            if (!s.type.equals("Break") && s.day >= 0 && s.day < 5) {
                String mainDiv = s.division.substring(0, 1);
                usedDays.computeIfAbsent(mainDiv, k -> new HashSet<>()).add(s.day);
            }
        }
        for (String div : Arrays.asList("A", "B", "C", "D")) {
            if (usedDays.getOrDefault(div, Collections.emptySet()).size() < 3) {
                return false;
            }
        }
        return true;
    }

    static int getDivisionIndex(String division) {
        switch (division.charAt(0)) {
            case 'A': return 0;
            case 'B': return 1;
            case 'C': return 2;
            default:  return 3; // D
        }
    }
    static void print2DTimetables() {
        String[] divisions = {"A", "B", "C", "D"};
        int startHour = 8;
        int endHour = 18;

        for (String div : divisions) {
            System.out.println("\n=====================================================");
            System.out.println("               Timetable for Division " + div);
            System.out.println("=====================================================");

            // Initialize timetable grid
            String[][] timetable = new String[5][endHour - startHour];

            for (int i = 0; i < 5; i++) {
                Arrays.fill(timetable[i], "--");
            }

            for (Session s : events) {
                if (!s.division.startsWith(div)) continue;
                if (s.day == -1 || s.startHour == -1) continue;

                int startIdx = s.startHour - startHour;
                String typeLabel = switch (s.type) {
                    case "Lecture" -> "(L)";
                    case "Tutorial" -> "(T)";
                    case "Lab" -> "(Lab)";
                    default -> "";
                };
                String display = s.subject + " " + typeLabel + " (" + s.faculty + ")";


                for (int i = 0; i < s.duration; i++) {
                    if (startIdx + i >= timetable[s.day].length) break;
                    timetable[s.day][startIdx + i] = display;
                }
            }

            // Print Header
            // Print Header
            // Print Header with wider columns
            System.out.printf("%-15s", "Day/Time");    // Slightly adjusted width for day column
            for (int h = startHour; h < endHour; h++) {
                System.out.printf("%-25s", h + "-" + (h + 1));
            }
            System.out.println();

// Print timetable rows with wider columns
            for (int day = 0; day < 5; day++) {
                System.out.printf("%-15s", dayName[day]);
                for (int h = 0; h < endHour - startHour; h++) {
                    System.out.printf("%-25s", timetable[day][h]);
                }
                System.out.println();
            }

        }
    }


}
