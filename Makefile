ifeq ($(OS), Windows_NT)
  sep=;
else
  sep=:
endif

srcdir=src
builddir=build
# models=PublishModel SubscribeModel LaunchModel AsyncModel AsyncModel2 Qos2 Qos2Sleep Tcp TcpSleep NoClean Reconnect ReconnectRetain ReconnectAsync ReconnectRetainAll ReconnectAsyncAll TwoClients
models=$(basename $(notdir $(wildcard src/*)))
pahojar=paho/org.eclipse.paho.client.mqttv3/target/org.eclipse.paho.client.mqttv3-1.1.1.jar
targ=$(addprefix $(builddir)/, $(addsuffix .class, $(models)) $(addsuffix .png, $(models)) $(addsuffix .dot, $(models)) $(addsuffix .pdf, $(models))) $(addprefix log/, $(models))
modbat=modbat/build/modbat.jar
mjar=../$(modbat)
ccp=$(subst ;,$(sep), ".;$(modbat);$(pahojar)")
rcp=$(subst ;,$(sep), ".;../$(pahojar)")
opts=--log-level=fine

.PHONY: all pahojar *.run pdfs pngs figs clean

all: $(targ)

pahojar: $(pahojar)

$(pahojar):
	cd paho/org.eclipse.paho.client.mqttv3/; \
	mvn package -DskipTests

modbat: $(modbat)

$(modbat):
	cd modbat/; \
	ant app-jar

$(builddir)/%.class: $(srcdir)/%.scala $(pahojar) $(modbat)
	scalac -d $(builddir) -cp $(ccp) $<

%.run: log/% $(builddir)/%.class $(pahojar) $(modbat)
	$(RM) log/$(basename $@)/*
	cd $(builddir); \
	scala -cp $(rcp) $(mjar) $(opts) --log-path=../log/$(basename $@) $(basename $@)
	# scala -cp $(rcp) $(mjar) $(opts) --log-path=../log/$(basename $@) --dotify-coverage $(basename $@)

$(builddir)/%.dot: $(builddir)/%.class $(modbat)
	mkdir -p log/$(basename $(notdir $@))
	cd $(builddir); \
	scala -cp $(rcp) $(mjar) --mode=dot $(basename $(notdir $@))

$(builddir)/%.png: $(builddir)/%.dot
	dot -Tpng '$<' -o'$@'

$(builddir)/%.pdf: $(builddir)/%.dot
	dot -Tpdf '$<' -o'$@'

pdfs: $(addsuffix .pdf, $(basename $(wildcard build/*.dot)))
pngs: $(addsuffix .png, $(basename $(wildcard build/*.dot)))
figs: pdfs pngs

log/%:
	mkdir $@

clean:
	git clean -fX
