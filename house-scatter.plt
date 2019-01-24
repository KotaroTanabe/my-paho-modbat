set output OUTPUT
set size square

stats INPUT nooutput

max = ceil(STATS_max_x > STATS_max_y ? STATS_max_x : STATS_max_y)

set title "Maximum difference between target temperatures and\nactual temperatures for each test case"

set xrange [0:max]
set xlabel "System A"
set ylabel "System B"
set key top left
set key box

plot INPUT u 1:2 w p t "test cases", "+" u (STATS_mean_x):(STATS_mean_y) t "mean", x w l t ""
