set output OUTPUT

set cbrange [10:25]
set xrange [-0.5:3.5]
set yrange [-0.5:3.5]
set xtics 1
set ytics ("a" 0, "b" 1, "c" 2, "d" 3)
set cblabel "room temperature"
unset key

do for [i = 0 : STEPS - 1] {
comment = system(sprintf("grep '#' %s | sed -n '%dp'", INPUT, i+1))
outside = real(system(sprintf("echo '%s' | cut -d ' ' -f 2", comment)))
target = real(system(sprintf("echo '%s' | cut -d ' ' -f 3", comment)))
set title sprintf("%s (step=%03d) target=%.1f outside=%.2f", TITLE, i, target, outside)
plot INPUT index i using 1:2:3 with image, \
     INPUT index i using 1:2:(sprintf("%.2f\n%g", $3, $4)) with labels
}

set out
