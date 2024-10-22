FreqScopeView2 {

	var <scope;
	var <scopebuf;
	var <server;
	var <active, <synth, <inBus, <dbRange, dbFactor, rate, <freqMode;
	var <maxVal; //only in linear
	var <bufSize;	// size of FFT
	var <ampdb;
	var <numChannels;
	var <>specialSynthDef, <specialSynthArgs; // Allows to override the analysis synth
	var playRoutine;
	var <minFreq, <maxFreq;
	var <processingRate; // \audio or \control
	var <fftBuffers, <fftCopyBuffers;
	var <>smooth = true; //change before staring the scope - creates smooth transitions between frames

	classvar scopeID = 0; //to create new synths

	/**initClass {
		StartUp.add {
			this.initSynthDefs;
		}
	}*/

	*new { arg parent, bounds, server, fftsize, numChannels, processingRate, ampdb = true;
		^super.new.initFreqScope (parent, bounds, server, fftsize, numChannels, processingRate, ampdb);
	}

	initSynthDefs {
		//do at start time, not init
		// dbFactor -> 2/dbRange
		// "ampdb: ".post; ampdb.postln;
		// linear
		SynthDef("system" ++ scopeID ++ "_freqScope2_0", { arg in=0, fftBufSize = 2048, scopebufnum=1, rate=4, dbFactor = 0.02;
			var phase = 1 - (rate * fftBufSize.reciprocal);
			var signal, chain, result, phasor, numSamples, mul, add;
			// var fftbufnum = numChannels.collect({LocalBuf(fftBufSize, 1)});
			var fftbufnum = \fftBuffers.kr(0 ! numChannels).asArray;
			mul = 0.00285;
			numSamples = (BufSamples.ir(fftbufnum) - 2) * 0.5; // 1023 (bufsize=2048)
			signal = In.ar(in, numChannels);
			chain = FFT(fftbufnum, signal, hop: 0.75, wintype:1);
			chain = PV_MagSmear(chain, 1);
			// -1023 to 1023, 0 to 2046, 2 to 2048 (skip first 2 elements DC and Nyquist)
			phasor = LFSaw.ar(rate/BufDur.ir(fftbufnum), phase, numSamples, numSamples + 2);
			phasor = phasor.round(2); // the evens are magnitude
			ScopeOut.ar( ((numChannels.collect({|inc| BufRd.ar(1, fftbufnum[inc], phasor, 1, 1) * mul})).ampdb * dbFactor) + 1, scopebufnum);
		}, [\kr, \ir, \ir, \ir, \kr]).add;
/*		SynthDef("system" ++ scopeID ++ "_freqScope2_0_shm", { arg in=0, fftBufSize = 2048, scopebufnum=1, rate=4, dbFactor = 0.02;
			var phase = 1 - (rate * fftBufSize.reciprocal);
			var signal, chain, result, phasor, numSamples, mul, add;
			var fftbufnum = numChannels.collect({LocalBuf(fftBufSize, 1)});
			mul = 0.00285;
			numSamples = (BufSamples.ir(fftbufnum) - 2) * 0.5; // 1023 (bufsize=2048)
			signal = In.ar(in, numChannels);
			chain = FFT(fftbufnum, signal, hop: 0.75, wintype:1);
			chain = PV_MagSmear(chain, 1);
			// -1023 to 1023, 0 to 2046, 2 to 2048 (skip first 2 elements DC and Nyquist)
			phasor = LFSaw.ar(rate/BufDur.ir(fftbufnum), phase, numSamples, numSamples + 2);
			phasor = phasor.round(2); // the evens are magnitude
			ScopeOut2.ar( ((numChannels.collect({|inc| BufRd.ar(1, fftbufnum[inc], phasor, 1, 1) * mul})).ampdb * dbFactor) + 1, scopebufnum, fftBufSize/rate);
		}, [\kr, \ir, \ir, \ir, \kr]).add;*/
		SynthDef("system" ++ scopeID ++ "_freqScope2_0_shm", { arg in=0, fftBufSize = 2048, scopebufnum=1, rate=4, dbFactor = 0.02, minFreq = 0, maxFreq = 0, inMul = 1;
			var phase = 1 - (rate * fftBufSize.reciprocal);
			var signal, chain, result, phasor, numSamples, mul, add;
			// var fftbufnum = numChannels.collect({LocalBuf(fftBufSize, 1)});
			var fftbufnum = \fftBuffers.kr(0 ! numChannels).asArray;
			var minBin, maxBin, binWidthInHz;
			var analyzedSignal, oddEven, evenOdd, oddWriters, evenWriters, oddReaders, evenReaders, lagTime, whichSignal, chain2, trigger, triggers;
			if(ampdb, {
				mul = 0.00285;
			}, {
				mul = 0.125; //I don't know why, this gives about full scale for WhiteNoise.kr(1)
			});
			// numSamples = (BufSamples.ir(fftbufnum) - 2) * 0.5; // 1023 (bufsize=2048)
			numSamples = (BufSamples.ir(fftbufnum.first)) * 0.5; // leave DC in
			processingRate.switch(
				\audio, {
					signal = In.ar(in, numChannels);
					binWidthInHz = SampleRate.ir / fftBufSize / 2;
				},
				\control, {
					signal = In.kr(in, numChannels);
					binWidthInHz = ControlRate.ir / fftBufSize / 2;
				}
			);
			signal = signal * inMul;
			chain = FFT(fftbufnum, signal, hop: 0.75, wintype:1); //<<< add changing hop size!
			chain = PV_MagSmear(chain, 1);

			phasor = LFSaw.ar(rate/BufDur.ir(fftbufnum.first), phase, numSamples, numSamples); //leave DC in
			// phasor.poll(2, label: \phasor);
			minBin = (minFreq / binWidthInHz).round(1);//.max(0);
			maxBin = (maxFreq / binWidthInHz).round(1);
			phasor = phasor.linlin(0, fftBufSize, minBin, maxBin);
			phasor = phasor.round(2); // the evens are magnitude

			analyzedSignal = numChannels.collect({|inc| BufRd.ar(1, fftbufnum[inc], phasor, 1, 1)});
			analyzedSignal = analyzedSignal * mul;

			if(ampdb, {
				ScopeOut2.ar( (analyzedSignal.ampdb * dbFactor) + 1, scopebufnum, fftBufSize/rate);
			}, {
				ScopeOut2.ar( analyzedSignal - 1, scopebufnum, fftBufSize/rate);
			});
		}, [\kr, \ir, \ir, \ir, \kr]).add;

		SynthDef("system" ++ scopeID ++ "_freqScope2_0_shm_smooth", { arg in=0, fftBufSize = 2048, scopebufnum=1, rate=4, dbFactor = 0.02, minFreq = 0, maxFreq = 0, inMul = 1;
			var phase = 1 - (rate * fftBufSize.reciprocal);
			var signal, chain, result, phasor, numSamples, mul, add;
			// var fftbufnum = numChannels.collect({LocalBuf(fftBufSize, 1)});
			var fftbufnum = \fftBuffers.kr(0 ! numChannels).asArray;
			var fftcopybufnum = \fftCopyBuffers.kr(0 ! numChannels).asArray;
			var minBin, maxBin, binWidthInHz;
			var analyzedSignal, oddEven, evenOdd, oddWriters, evenWriters, oddReaders, evenReaders, lagTime, whichSignal, chain2, trigger, triggers;
			if(ampdb, {
				mul = 0.00285;
			}, {
				mul = 0.125; //I don't know why, this gives about full scale for WhiteNoise.kr(1)
			});
			// numSamples = (BufSamples.ir(fftbufnum) - 2) * 0.5; // 1023 (bufsize=2048)
			numSamples = (BufSamples.ir(fftbufnum.first)) * 0.5; // leave DC in
			processingRate.switch(
				\audio, {
					signal = In.ar(in, numChannels);
					binWidthInHz = SampleRate.ir / fftBufSize / 2;
				},
				\control, {
					signal = In.kr(in, numChannels);
					binWidthInHz = ControlRate.ir / fftBufSize / 2;
				}
			);
			signal = signal * inMul;
			chain = FFT(fftbufnum, signal, hop: 0.75, wintype:1); //<<< add changing hop size!
			chain = PV_MagSmear(chain, 1);

			phasor = LFSaw.ar(rate/BufDur.ir(fftbufnum.first), phase, numSamples, numSamples); //leave DC in
			// phasor.poll(2, label: \phasor);
			minBin = (minFreq / binWidthInHz).round(1);//.max(0);
			maxBin = (maxFreq / binWidthInHz).round(1);
			phasor = phasor.linlin(0, fftBufSize, minBin, maxBin);
			phasor = phasor.round(2); // the evens are magnitude


			//smoothing
			chain2 = PV_Copy(chain, fftcopybufnum);
			trigger = chain.asArray.first >= 0;
			oddEven = (ToggleFF.kr(trigger));
			evenOdd = oddEven.neg + 1; //invert
			chain = numChannels.collect({|inc| PV_MagFreeze(chain[inc], oddEven)});
			chain2 = numChannels.collect({|inc| PV_MagFreeze(chain2[inc], evenOdd)});
			chain = PV_MagFreeze(chain, oddEven);
			chain2 = PV_MagFreeze(chain2, evenOdd);
			lagTime = Timer.kr(trigger);
			whichSignal = VarLag.kr(oddEven, lagTime, warp: \sine);
			analyzedSignal = LinSelectX.ar(whichSignal, [
				numChannels.collect({|inc| BufRd.ar(1, fftbufnum[inc], phasor, 1, 1)}),
				numChannels.collect({|inc| BufRd.ar(1, fftcopybufnum[inc], phasor, 1, 1)})
			]);

			analyzedSignal = analyzedSignal * mul;

			if(ampdb, {
				ScopeOut2.ar( (analyzedSignal.ampdb * dbFactor) + 1, scopebufnum, fftBufSize/rate);
			}, {
				ScopeOut2.ar( analyzedSignal - 1, scopebufnum, fftBufSize/rate);
			});
		}, [\kr, \ir, \ir, \ir, \kr]).add;

		// logarithmic
		SynthDef("system" ++ scopeID ++ "_freqScope2_1", { arg in=0, fftBufSize = 2048, scopebufnum=1, rate=4, dbFactor = 0.02;
			var phase = 1 - (rate * fftBufSize.reciprocal);
			var signal, chain, result, phasor, halfSamples, mul, add;
			// var fftbufnum = numChannels.collect({LocalBuf(fftBufSize, 1)});
			var fftbufnum = \fftBuffers.kr(0 ! numChannels).asArray;
			mul = 0.00285;
			halfSamples = BufSamples.ir(fftbufnum) * 0.5;
			signal = In.ar(in, numChannels);
			chain = FFT(fftbufnum, signal, hop: 0.75, wintype:1);
			chain = PV_MagSmear(chain, 1);
			phasor = halfSamples.pow(LFSaw.ar(rate/BufDur.ir(fftbufnum), phase, 0.5, 0.5)) * 2; // 2 to bufsize
			phasor = phasor.round(2); // the evens are magnitude
			ScopeOut.ar( ((numChannels.collect({|inc| BufRd.ar(1, fftbufnum[inc], phasor, 1, 1) * mul})).ampdb * dbFactor) + 1, scopebufnum);
		}, [\kr, \ir, \ir, \ir, \kr]).add;

		SynthDef("system" ++ scopeID ++ "_freqScope2_1_shm", { arg in=0, fftBufSize = 2048, scopebufnum=1, rate=4, dbFactor = 0.02;
			var phase = 1 - (rate * fftBufSize.reciprocal);
			var signal, chain, result, phasor, halfSamples, mul, add;
			// var fftbufnum = numChannels.collect({LocalBuf(fftBufSize, 1)});
			var fftbufnum = \fftBuffers.kr(0 ! numChannels).asArray;
			mul = 0.00285;
			halfSamples = BufSamples.ir(fftbufnum) * 0.5;
			signal = In.ar(in, numChannels);
			chain = FFT(fftbufnum, signal, hop: 0.75, wintype:1);
			chain = PV_MagSmear(chain, 1);
			phasor = halfSamples.pow(LFSaw.ar(rate/BufDur.ir(fftbufnum), phase, 0.5, 0.5)) * 2; // 2 to bufsize
			phasor = phasor.round(2); // the evens are magnitude
			ScopeOut2.ar( ((numChannels.collect({|inc| BufRd.ar(1, fftbufnum[inc], phasor, 1, 1) * mul})).ampdb * dbFactor) + 1, scopebufnum, fftBufSize/rate);
		}, [\kr, \ir, \ir, \ir, \kr]).add;
	}

	initFreqScope { arg parent, bounds, argServer, fftsize, numCh, rateArg, dB;
		server = argServer ? Server.default;
		bufSize = fftsize;
		numChannels = numCh;
		ampdb = dB;
		// "ampdb in initFreqScope: ".post; ampdb.postln;
		// "db: ".post; dB.postln;
		if (this.shmScopeAvailable) {
			scope = ScopeView.new(parent, bounds);
			scope.server = server;
			scope.fill = false;
		} {
			scope = SCScope(parent, bounds);
		};
		scope.style_(1); //overlay channels

		processingRate = rateArg;

		active = false;
		inBus = 0;
		dbRange = 96;
		dbFactor = 2/dbRange;
		maxVal = 1;
		rate = 4;
		freqMode = 0; // 0 - linear
		/*bufSize = 2048;*/
		minFreq ?? {minFreq = 0};
		maxFreq ?? {
			if(processingRate == \audio, {
				maxFreq = server.sampleRate / 2;
			}, {
				maxFreq = (server.sampleRate / server.options.blockSize) / 2;
			})
		};
		ServerQuit.add(this, server);
		scopeID = scopeID + 1; //increment, to have unique sytnhs
		^this;
	}

	allocBuffers {
		if (this.shmScopeAvailable) {
			scopebuf = ScopeBuffer.alloc(server, numChannels);
			scope.bufnum = scopebuf.bufnum;
		} {
			Buffer.alloc(server, bufSize/4, numChannels, { |sbuf|
				scope.bufnum = sbuf.bufnum;
				scopebuf = sbuf;
			});
		};
	}

	allocFftBuffers {
		fftBuffers = numChannels.collect({Buffer.alloc(server, bufSize, 1)}); //bufSize == fftsize
		if(this.smooth, {
			fftCopyBuffers = numChannels.collect({Buffer.alloc(server, bufSize, 1)}); //bufSize == fftsize
		});
	}

	freeBuffers {
		if (scopebuf.notNil) {
			scopebuf.free; scopebuf = nil;
		};
		if (fftBuffers.notNil) {
			fftBuffers.do(_.free); fftBuffers = nil;
		};
		if (fftCopyBuffers.notNil) {
			fftCopyBuffers.do(_.free); fftCopyBuffers = nil;
		};
	}

	start {
		var defname, args;


		if (synth.notNil) { synth.free };
		if (scopebuf.isNil) { this.allocBuffers };
		if (fftBuffers.isNil) { this.allocFftBuffers };

		this.initSynthDefs; //rutime, to adjust numChannels
		playRoutine = forkIfNeeded{
			server.sync;
			defname = specialSynthDef ?? {
				"system" ++ scopeID ++ "_freqScope2_" ++ freqMode.asString ++ if (this.shmScopeAvailable) {"_shm"} {""} ++ if (this.smooth) {"_smooth"} {""}
			};
			args = [\in, inBus, \dbFactor, dbFactor, \rate, rate, \fftBufSize, bufSize,
				\minFreq, minFreq, \maxFreq, maxFreq,
				\inMul, maxVal.max(0.001).reciprocal,
				\scopebufnum, scopebuf.bufnum,
				\fftBuffers, fftBuffers
			];
			if(this.smooth, {args = args ++ [\fftCopyBuffers, fftCopyBuffers]});
			args = args ++ specialSynthArgs;
			synth = Synth.tail(RootNode(server), defname, args);
			if (scope.isKindOf(ScopeView)) {{ scope.start }.defer};
		}

	}

	kill {
		playRoutine.stop;
		this.active_(false);
		this.freeBuffers;
		ServerQuit.remove(this, server);
	}

	active_ { arg activate;
		if (activate) {
			ServerTree.add(this, server);
			if (server.serverRunning) {
				active=activate;
				this.doOnServerTree;
				^this
			}
		} {
			ServerTree.remove(this, server);
			if (server.serverRunning and: active) {
				if (scope.isKindOf(ScopeView)) { scope.stop };
				playRoutine.stop;
				synth.free;
				synth = nil;
			};
		};
		active=activate;
		^this
	}

	doOnServerTree {
		synth = nil;
		if (active) { this.start; }
	}

	doOnServerQuit {
		var thisScope = scope;
		defer {
			thisScope.stop;
		};
		scopebuf = synth = nil;
	}

	inBus_ { arg num;
		inBus = num;
		if(active, {
			synth.set(\in, inBus);
		});
		^this
	}

	dbRange_ { arg db;
		dbRange = db;
		dbFactor = 2/db;
		if(active, {
			synth.set(\dbFactor, dbFactor);
		});
	}

	maxVal_ { arg val;
		maxVal = val;
		// format("setting new maxval in scope to % (reciprocal: %)", maxVal, maxVal.max(0.001).reciprocal).postln;
		if(active, {
			synth.set(\inMul, maxVal.max(0.001).reciprocal);
		});
	}

	freqMode_ { arg mode;
		freqMode = mode.asInteger.clip(0,1);
		if(freqMode == 1) {
			minFreq = server.sampleRate / bufSize;
			maxFreq = server.sampleRate / 2;
		};
		if(active, {
			this.start;
		});
	}

	minFreq_{arg freq;
		if(freqMode == 0) {
			minFreq = freq;
			if(active, {
				synth.set(\minFreq, minFreq);
			})
		} {
			"frequency range setting available only in linear mode currently".warn;
		};
	}

	maxFreq_{arg freq;
		if(freqMode == 0) {
			maxFreq = freq;
			if(active, {
				synth.set(\maxFreq, maxFreq);
			});
		} {
			"frequency range setting available only in linear mode currently".warn;
		};
	}

	specialSynthArgs_ {|args|
		specialSynthArgs = args;
		if(args.notNil and:{active}){
			synth.set(*specialSynthArgs);
		}
	}

	special { |defname, extraargs|
		this.specialSynthDef_(defname);
		this.specialSynthArgs_(extraargs);
		if(active, {
			this.start;
		});
	}

	*response{ |parent, bounds, bus1, bus2, freqMode=1|
		var scope = this.new(parent, bounds, bus1.server).inBus_(bus1.index);
		var synthDefName = "system_freqScope%_magresponse%".format(freqMode, if (scope.shmScopeAvailable) {"_shm"} {""});

		^scope.special(synthDefName, [\in2, bus2])
	}

	doesNotUnderstand { arg selector ... args;
		^scope.performList(selector, args);
	}

	shmScopeAvailable {
		^server.isLocal
		// and: { server.inProcess.not }
	}
}

FreqScope2 {
	var <bus, <fftSize, <name, <bounds, <parent, <server, <ampdb, <smooth;
	var <scopeOpen;
	var busNum;

	var <scope, <window;
	var <numChannels, <rate;
	var <colors;
	var <isRunning = true;
	var <>drawXGrid = true, <>drawYGrid = true;

	var minFreq = 0, maxFreq = 22000, nyquist = 22000;
	var <grid, <xSpec, <ySpec, <gridView;

	var xInset = 16, yInset = 14;

	/**new { arg width=522, height=300, busNum=0, scopeColor, bgColor, server;*/
	*new { arg bus = 0, fftSize = 2048, name = "Frequency Analyzer", bounds, parent, server, ampdb = true, smooth = false;
	^super.newCopyArgs(bus, fftSize, name, bounds, parent, server, ampdb, smooth).init;
}

init {
		var width=522, height=300;//, scopeColor, bgColor, server;
		var rect, pad, font, freqLabel, freqLabelDist, dbLabel, dbLabelDist;
		/*var setFreqLabelVals, setDBLabelVals;*/
		var nyquistKHz;
		var mainLayout, lView, rLayout, rView, scopeView;

		if(bus.isKindOf(Bus), {
			numChannels = bus.numChannels;
			rate = bus.rate;
			server = bus.server;
			}, {
				numChannels = 1;
				rate = \audio; //assume audio rate, when bus number is provided
				});

				server ?? {server = Server.default};



		busNum = bus.asControlInput;
		/*busNum = busNum.asControlInput;*/
		if(scopeOpen != true, { // block the stacking up of scope windows
			//make scope

			/*scopeColor = scopeColor ?? { Color.new255(255, 218, 000) };*/

			scopeOpen = true;

			server = server ? Server.default;

			/*rect = Rect(0, 0, width, height);*/
			bounds ?? {bounds = Rect(0, 0, width, height)};
			rect = bounds;
			// pad = [30, 48, 14, 10]; // l,r,t,b
			font = Font.monospace(9);
			freqLabel = Array.newClear(12);
			freqLabelDist = rect.width/(freqLabel.size-1);
			dbLabel = Array.newClear(17);
			dbLabelDist = rect.height/(dbLabel.size-1);

			rate.switch(
				\audio, {
					// nyquistKHz = server.sampleRate / 2;
					maxFreq = server.sampleRate / 2;
				},
				\control, {
					maxFreq = (server.sampleRate / server.options.blockSize) / 2;
				}
			);
			// nyquistKHz = server.sampleRate;
			// if( (nyquistKHz == 0) || nyquistKHz.isNil, {
			// 	nyquistKHz = 22.05 // best guess?
			// 	},{
			// 		nyquistKHz = nyquistKHz * 0.0005;
			// });

			// maxFreq = nyquistKHz * 1000;

			nyquist = maxFreq;

			grid = DrawGrid();

			/*window = Window("Freq Analyzer", rect.resizeBy(pad[0] + pad[1] + 4, pad[2] + pad[3] + 4), false);*/

			if(parent.notNil, {
				window = View(parent, bounds);
			}, {
				window = Window(name, bounds, true).front;
			});
			window.layout_(
				mainLayout = HLayout(lView = View()).margins_([4, 12, 12, 4]).spacing_(0);
			);

			lView.layout_(StackLayout().mode_(1)); //mode 1 = display all the views
			gridView = UserView(lView);
			scopeView = View(lView).layout_(HLayout().margins_([xInset, 0, xInset, yInset]));
			gridView.background = Color.clear;
			gridView.drawFunc = {|thisView|
				var xGridBounds, yGridBounds;
				if(isRunning, {
					grid.bounds = thisView.bounds.insetAll(xInset, 0, xInset, yInset);
					grid.draw;

				})
			};


			/*freqLabel.size.do({ arg i;
				freqLabel[i] = StaticText(window, Rect(pad[0] - (freqLabelDist*0.5) + (i*freqLabelDist), pad[2] - 10, freqLabelDist, 10))
					.font_(font)
					.align_(\center)
				;
				StaticText(window, Rect(pad[0] + (i*freqLabelDist), pad[2], 1, rect.height))
					.string_("")
				;
			});*/

			/*dbLabel.size.do({ arg i;
				dbLabel[i] = StaticText(window, Rect(0, pad[2] + (i*dbLabelDist), pad[0], 10))
					.font_(font)
					.align_(\left)
				;
				StaticText(window, Rect(pad[0], dbLabel[i].bounds.top, rect.width, 1))
					.string_("")
				;
			});*/

			/*scope = FreqScopeView2(window, rect.moveBy(pad[0], pad[2]), server);*/

			scope = FreqScopeView2(scopeView, nil, server, fftSize, numChannels, rate, ampdb);//, rect.moveBy(pad[0], pad[2]), server);
			/*scope.xZoom_((scope.bufSize*0.25) / width);*/
			scope.smooth_(this.smooth);

			// "scope: ".post; scope.postln;


			/*setFreqLabelVals.value(scope.freqMode, 2048);*/
			/*setFreqLabelVals.value(scope.freqMode, scope.bufSize);
			setDBLabelVals.value(scope.dbRange);*/
			this.setFreqLabel;
			this.setAmpLabel(if(ampdb, {scope.dbRange}, {1}));
			gridView.refresh;

			/*Button(window, Rect(pad[0] + rect.width, pad[2], pad[1], 16))*/
			mainLayout.add(
				rView = View(window)
			);
			rLayout = VLayout();
			rView.layout_(rLayout).fixedWidth_(80);

			Button(rView)
				.states_([["stop", Color.white, Color.green(0.5)], ["start", Color.white, Color.red(0.5)]])
				.action_({ arg view;
					if(view.value == 0, {
						scope.active_(true);
					},{
						scope.active_(false);
					});
				})
				.font_(font)
				.canFocus_(false)
			;
			/*,*/

			/*StaticText(window, Rect(pad[0] + rect.width, pad[2]+20, pad[1], 10))*/
			StaticText(rView)
				.string_("BusIn")
				.font_(font)
			;
			/*,*/

			/*NumberBox(window, Rect(pad[0] + rect.width, pad[2]+30, pad[1], 14))*/
			NumberBox(rView)
				.action_({ arg view;
					view.value_(view.value.asInteger.clip(0, server.options.numAudioBusChannels));
					scope.inBus_(view.value);
				})
				.value_(busNum)
				.font_(font)
			;

			StaticText(rView)
				.string_("numChannels:")
				.font_(font)
			;

			StaticText(rView)
				.string_(numChannels.asString)
				.font_(font)
			;

			StaticText(rView)
				.string_("fftSize:")
				.font_(font)
			;

			StaticText(rView)
				.string_(fftSize.asString)
				.font_(font)
			;

			StaticText(rView)
				.string_("rate:")
				.font_(font)
			;

			StaticText(rView)
				.string_(rate.asString)
				.font_(font)
			;
			/*,*/

			/*,*/

			/*StaticText(window, Rect(pad[0] + rect.width, pad[2]+48, pad[1], 10))*/
			/*StaticText(rView)
				.string_("FrqScl")
				.font_(font)
			;*/
			/*,*/
			StaticText(rView)
			.string_("freqMode:")
			.font_(font)
			;
			PopUpMenu(rView)
			.items_(["lin", "log"])
			.action_({ arg view;
				this.freqMode_(view.value)
			})
			.canFocus_(false)
			.font_(font)
			.valueAction_(1)
			;

			StaticText(rView)
			.string_("dbCut:")
			.font_(font)
			;
			PopUpMenu(rView)
				.items_(Array.series(12, 12, 12).collect({ arg item; item.asString }))
				.action_({ arg view;
				if(ampdb, {
					scope.dbRange_((view.value + 1) * 12);
					/*setDBLabelVals.value(scope.dbRange);*/
					this.setAmpLabel(scope.dbRange);
				}, {
					"setting db scale requires ampdb to be true".warn;
				});
				})
				.canFocus_(false)
				.font_(font)
				.value_(7)
			;
			/*,*/
			rLayout.add(nil);

			scope
			.inBus_(busNum)
			.active_(true)
			.style_(1)
			/*.waveColors_([scopeColor.alpha_(1)])*/
			/*.waveColors_([scopeColor.alpha_(1)])*/
			.waveColors_(numChannels.collect({Color.hsv(rrand(0.2, 0.8), 0.5, 0.6)}))
			// .background_(Color.white(0.8))
			.canFocus_(false)
			;

			/*if (bgColor.notNil) {
				scope.background_(bgColor)
			};*/
				scope.background_(Color.white);

			window.onClose_({
				scope.kill;
				scopeOpen = false;
			});
			/*^super.newCopyArgs(scope, window)*/
		});
	}

	freqMode_ { |mode|
		scope.freqMode = mode.asInteger.clip(0,1);
		if(scope.freqMode == 1) {
			minFreq = server.sampleRate / scope.bufSize;
			maxFreq = server.sampleRate / 2;
		};
		this.setFreqLabel;
	}

	setFreqLabel {arg minFreqArg, maxFreqArg;
		minFreqArg !? {minFreq = minFreqArg};
		maxFreqArg !? {maxFreq = maxFreqArg};
		xSpec = ControlSpec(minFreq.max(0.01), maxFreq, [\lin, \exponential][scope.freqMode], units: "Hz");
		grid.horzGrid_(xSpec.grid);
		gridView.refresh;
	}
	setAmpLabel {arg val; //FIXME: in db mode, sets min db value; in amp mode, sets max value
		if(ampdb, {
			var db = val;
			ySpec = ControlSpec(db.neg, 0, \lin, units: "dB");
		}, {
			// "using hardcoded scale 0-1".warn;
			// format("setting amp label to %", val).postln;
			ySpec = ControlSpec(0, val, \lin);
			scope.maxVal = val;
		});
		grid.vertGrid_(ySpec.grid);
		gridView.refresh;
	}

	freqRange_ {arg min, max;
		var zoom, offset, freqDiff;
		freqDiff = max - min;
		zoom = nyquist / freqDiff;
		// scope.xZoom_(zoom);
		// scope.x = min/nyquist * (fftSize/2).neg;
		scope.minFreq_(min);
		scope.maxFreq_(max);
		this.setFreqLabel(min, max);
		//!!!!! scope x offset is in pixels, fftSize/2 is full width, then scaled by zoom!
		//scope xZoom is a ratio, 1 is full
	}

	freqRange {
		^[minFreq, maxFreq]
	}

	background_ {arg newColor;
		scope.background_(newColor);
	}
	background {
		^scope.background;
	}
	channelColors_ {arg newColors;
		scope.waveColors_(newColors);
	}
	channelColors {
		^scope.waveColors;
	}
	free {
		scope.kill;
		window.close;
	}
}
