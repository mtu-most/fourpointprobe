; This gcode file is used for controlling printer to perform automated four point probe measurements
G1 Z20 F3000; raise head first to protect probe
G28; home axis
G21 ; set units to mm
G90 ; set positioning to absolute
G1 Z5 F3000; move head to raised position
G1 X100 Y83 F1000 ; move head to center
G1 X96.666664 Y79.111115 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y79.111115 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y79.111115 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y79.111115 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y79.111115 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y80.22222 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y80.22222 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y80.22222 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y80.22222 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y80.22222 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y81.333336 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y81.333336 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y81.333336 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y81.333336 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y81.333336 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y82.44444 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y82.44444 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y82.44444 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y82.44444 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y82.44444 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y83.55556 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y83.55556 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y83.55556 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y83.55556 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y83.55556 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y84.666664 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y84.666664 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y84.666664 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y84.666664 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y84.666664 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y85.77778 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y85.77778 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y85.77778 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y85.77778 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y85.77778 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X96.666664 Y86.888885 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X98.333336 Y86.888885 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X100.0 Y86.888885 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X101.666664 Y86.888885 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G1 X103.333336 Y86.888885 F1000
G1 Z0 F3000; lower head
G4 P3000; pause for measurement
G1 Z5 F3000; raise head
G28; home axis
G1 Z20 F3000; raise head
M84 ; disable motors
