set output OUTPUT

stats INPUT0 u 3 name "A" nooutput
stats INPUT1 u 3 name "B" nooutput

range_min = floor(A_min < B_min ? A_min : B_min)
range_max = ceil(A_max > B_max ? A_max : B_max)

set xrange [-0.5:3.5]
set yrange [-0.5:3.5]
set xtics 1
set ytics ("a" 0, "b" 1, "c" 2, "d" 3)
set cbtics 1
unset key

do for [i = 0 : STEPS - 1] {
comment = system(sprintf("grep '#' %s | sed -n '%dp'", INPUT0, i+1))
outside = real(system(sprintf("echo '%s' | cut -d ' ' -f 2", comment)))
target = real(system(sprintf("echo '%s' | cut -d ' ' -f 3", comment)))
set multiplot layout 2,2 title sprintf("%s (step=%03d) target=%.1f outside=%.2f", TITLE, i, target, outside)
set cbrange [range_min : range_max]
set cblabel "room temperature"
set title "System A"
plot INPUT0 index i using 1:2:3 with image, \
     INPUT0 index i using 1:2:(sprintf("%.2f\n%g", $3, $4)) with labels
set title "System B"
plot INPUT1 index i using 1:2:3 with image, \
     INPUT1 index i using 1:2:(sprintf("%.2f\n%g", $3, $4)) with labels
set title "Delta (B - A)"
set cbrange [-3 : 3]
set cblabel "difference"
plot MERGED index i using 1:2:($7-$3) with image, \
     MERGED index i using 1:2:(sprintf("%.2f", $7-$3)) with labels
set title "Diff of error (|B - target| - |A - target|)"
set cbrange [-3 : 3]
set cblabel "difference"
plot MERGED index i using 1:2:(abs($7-target) - abs($3-target)) with image, \
     MERGED index i using 1:2:(sprintf("%.2f", abs($7-target) - abs($3-target))) with labels
unset multiplot
}
