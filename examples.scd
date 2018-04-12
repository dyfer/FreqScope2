Server.killAll
s.boot

LineLayout
s.boot

b=Bus.control(s, 2);

f = FreqScope2(Bus.new(\audio,0, 2, s), 16384, "test")
f = FreqScope2(b, 512)
FreqScope2(0)
f.scope.scope.waveColors_([Color.black, Color.red])
f.scope.scope.background_(Color.white)
f.dump
f.scope.scope.xZoom = 1
f.scope.scope.x = 2500/22000 * (1024/2).neg
f.free
f.freqRange_(0, 60)
f.scope.background = Color.white

a = 24000 / 2048
a
b = 24000/a


5000/22000 * (1024/2).neg

//!!!!! scope x = in pixels, width/2 is full window

f.scope.bounds
f.scope.scope.x = 250
f.scope.scope.xZoom

s.quit

n.free; n = {PinkNoise.ar([-24.dbamp, -12.dbamp])}.play
n.free; n = {PinkNoise.ar([0, -12.dbamp])}.play
n.free
n.free; n = {SinOsc.ar(Line.kr(20, 22000, 10), 0, -12.dbamp)}.play;
n.free


n.free; n = {SinOsc.ar(60, 0, [-24.dbamp, -30.dbamp, -15.dbamp])}.play(outbus: ~obci[0].processedBus.index + 0 )

n.free; n = {Out.kr(b, SinOsc.ar(5, 0, -24.dbamp))}.play(outbus: 20);

b.scope

LivePlotter()


Window()
View()

Bus.audio(s, 3).rate
.numChannels
.asControlInput

FreqScope()

w = Window.new.front

w.layout = HLayout()

x = View(w).background_(Color.green)
y = View(w).background_(Color.blue)
w.layout

x.layout_(StackLayout())
z = UserView(x);
z.background_(Color.red)
StaticText(x).string_("DUAP")
x.layout.mode_(1)

l = UserView(x);
l.background_(Color.yellow)

DrawGrid.test(nil, ControlSpec(-96, 0, units: \dB).grid, 200@300);
DrawGrid.test(ControlSpec(1, 22050, -4, units: \Hz).grid, ControlSpec(-96, 0, units: \dB).grid, 600@300);

l = GridLines((ControlSpec(1, 22050, -4, units: \Hz)))
l.dump
l.niceNum(345.2, true)
l.ideals(10, 100, 5)
ControlSpec(0, 22000, \lin).unmap(1)
(0..22000).collect({|val|ControlSpec(0, 22000, -4).unmap(val)}).plot\


x = 20000; 10.pow(x.linlin(0, 22000, 0, log10(22000)))

10.pow(4.3)

v = 22000 / 11 * 5; ControlSpec(0, 22000, \lin).map(ControlSpec(0, 22000, -20).unmap(v))

log10(11000) / log10(22000)

x = 22000 / 4; 22000.pow(x / 22000);
x = 10; y = 1000;  (y.pow(x * (1/ 11)) - 1) / (y- 1) * 22000;
x = 12; y = 1024;  (y.pow(x * (1/ 23)) - 1) / (y- 1) * 22000;
x = 12000; y = 1024; x = x.linlin(0, 22000, 0, 11); (y.pow(x * (1/ 11)) - 1) / (y- 1) * 22000;

12.3.linlin(0, 22000, 0, 11)
- 1;

(0..22000).collect({|val| log(val/22000) }).plot

(0..22000).collect({|val|}).plot

\freq.asWarp


setFreqLabelVals = { arg mode, bufsize;
				var kfreq, factor, halfSize;
	var freqLabelSize = numTicks;

				factor = 1/(numTicks-1);
				halfSize = bufsize * 0.5;

				freqLabel.size.do({ arg i;
					if(mode == 1, {
						kfreq = (halfSize.pow(i * factor) - 1)/(halfSize-1) * nyquistKHz;
					},{