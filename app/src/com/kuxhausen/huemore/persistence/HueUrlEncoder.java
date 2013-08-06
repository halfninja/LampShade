package com.kuxhausen.huemore.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;

import android.util.Base64;
import android.util.Pair;

import com.kuxhausen.huemore.state.Event;
import com.kuxhausen.huemore.state.Mood;
import com.kuxhausen.huemore.state.api.BulbState;

public class HueUrlEncoder {

	public final static Integer PROTOCOL_VERSION_NUMBER = 1;
	
	
	public static String encode(Mood mood)
	{
		return encode(mood, null);
	}
	public static String encode(Mood mood, Integer[] bulbsAffected){
		if (mood == null)
			return "";
		
		ManagedBitSet mBitSet = new ManagedBitSet();
		
		// Set 3 bit protocol version
		mBitSet.addNumber(PROTOCOL_VERSION_NUMBER,3);
		
		//Flag if optional bulblist included
		mBitSet.incrementingSet(bulbsAffected!=null);
		
		//50 bit optional bulb inclusion flags
		if(bulbsAffected!=null){
			boolean[] bulbs = new boolean[50];
			for(Integer i: bulbsAffected){
				if(i!=null)
					bulbs[i-1]=true;
			}
			for(int i = 0; i<bulbs.length; i++)
				mBitSet.incrementingSet(bulbs[i]);
		}
		
		// Set 6 bit number of channels
		mBitSet.addNumber(mood.numChannels,6);
		
		addTimingRepeatPolicy(mBitSet, mood);
		
		ArrayList<Integer> timeArray = generateTimesArray(mood);
		// Set 6 bit number of timestamps
		mBitSet.addNumber(timeArray.size(),6);
		// Set variable size list of 20 bit timestamps
		for(Integer i : timeArray)
			mBitSet.addNumber(i,20);

		ArrayList<BulbState> stateArray = generateStatesArray(mood);
		// Set 6 bit number of states
		mBitSet.addNumber(stateArray.size(),6);
		
		for(BulbState state : stateArray)
			addState(mBitSet, state);
		
		// Set 8 bit number of events
		mBitSet.addNumber(mood.events.length,8);
		
		addListOfEvents(mBitSet, mood, timeArray, stateArray);		
		return mBitSet.getBase64Encoding();
	}
	
	/** Set 8 bit timing repeat policy **/
	private static void addTimingRepeatPolicy(ManagedBitSet mBitSet, Mood mood){
		//1 bit timing addressing reference mode
		mBitSet.incrementingSet(mood.timeAddressingRepeatPolicy);
		
		//7 bit timing repeat number (max value specialcased to infinity)
		mBitSet.addNumber(mood.getNumLoops(),7);
	}
	
	/** Set variable length state **/
	private static void addState(ManagedBitSet mBitSet, BulbState bs){
		/** Put 9 bit properties flags **/
		{
			// On/OFF flag always include in v1 implementation 1
			mBitSet.incrementingSet(true);
			
			// Put bri flag
			mBitSet.incrementingSet(bs.bri != null);
			
			// Put hue flag
			mBitSet.incrementingSet(bs.hue != null);
			
			// Put sat flag
			mBitSet.incrementingSet(bs.sat != null);

			// Put xy flag
			mBitSet.incrementingSet(bs.xy != null);

			// Put ct flag
			mBitSet.incrementingSet(bs.ct != null);

			// Put alert flag
			mBitSet.incrementingSet(bs.alert != null);

			// Put effect flag
			mBitSet.incrementingSet(bs.effect != null);

			// Put transitiontime flag
			mBitSet.incrementingSet(bs.transitiontime != null);
		}
		/** Put on bit **/
		// On/OFF flag always include in v1 implementation 1
		mBitSet.incrementingSet(bs.on);
		
		/** Put 8 bit bri **/	
		if (bs.bri != null) {
			mBitSet.addNumber(bs.bri,8);
		}
		
		/** Put 16 bit hue **/
		if (bs.hue != null) {
			mBitSet.addNumber(bs.hue,16);
		}

		/** Put 8 bit sat **/
		if (bs.sat != null) {
			mBitSet.addNumber(bs.sat,8);
		}
		
		/** Put 64 bit xy **/
		if (bs.xy != null) {
			int x = Float
					.floatToIntBits((float) ((double) bs.xy[0]));
			mBitSet.addNumber(x,32);

			int y = Float
					.floatToIntBits((float) ((double) bs.xy[1]));
			mBitSet.addNumber(y,32);
		}
		
		/** Put 9 bit ct **/
		if (bs.ct != null) {
			mBitSet.addNumber(bs.ct,9);
		}
		
		/** Put 2 bit alert **/
		if (bs.alert != null) {
			int value = 0;
			if (bs.alert.equals("none"))
				value = 0;
			else if (bs.alert.equals("select"))
				value = 1;
			else if (bs.alert.equals("lselect"))
				value = 2;

			mBitSet.addNumber(value,2);
		}
		
		/** Put 4 bit effect **/
		// three more bits than needed, reserved for future API
		// functionality
		if (bs.effect != null) {
			int value = 0;
			if (bs.effect.equals("none"))
				value = 0;
			else if (bs.effect.equals("colorloop"))
				value = 1;
			
			mBitSet.addNumber(value,4);
		}
		
		/** Put 16 bit transitiontime **/
		if (bs.transitiontime != null) {
			mBitSet.addNumber(bs.transitiontime,16);
		}
	}
	
	/** Set variable length list of variable length events **/
	private static void addListOfEvents(ManagedBitSet mBitSet, Mood mood, ArrayList<Integer> timeArray, ArrayList<BulbState> stateArray){
		String[] bulbStateToStringArray = new String[stateArray.size()];
		for(int i = 0; i< stateArray.size(); i++){
			bulbStateToStringArray[i] = stateArray.get(i).toString();
		}
		ArrayList<String> bulbStateToStringList = new ArrayList<String>(Arrays.asList(bulbStateToStringArray));
		for(Event e: mood.events){
			
			// add channel number
			mBitSet.addNumber(e.channel, getBitLength(mood.numChannels));
			
			//add timestamp lookup number
			mBitSet.addNumber(timeArray.indexOf(e.time), getBitLength(timeArray.size()));
			
			//add mood lookup number
			mBitSet.addNumber(bulbStateToStringList.indexOf(e.state.toString()), getBitLength(stateArray.size()));
		}
	}
	
	/** calulate number of bits needed to address this many addresses **/
	private static int getBitLength(int addresses){
		int length=0;
		while(addresses!=0){
			addresses = addresses>>>1;
			length++;
		}
		return length;
	}
	
	private static ArrayList<Integer> generateTimesArray(Mood mood){
		HashSet<Integer> timeset = new HashSet<Integer>();
		for(Event e : mood.events){
			timeset.add(e.time);
		}
		ArrayList<Integer> timesArray = new ArrayList<Integer>();
		timesArray.addAll(timeset);
		return timesArray;
	}
	
	private static ArrayList<BulbState> generateStatesArray(Mood mood){
		HashMap<String, BulbState> statemap = new HashMap<String, BulbState>();
		for(Event e : mood.events){
			statemap.put(e.state.toString(), e.state);
		}
		ArrayList<BulbState> statesArray = new ArrayList<BulbState>();
		statesArray.addAll(statemap.values());
		return statesArray;
	}
	
	private static BulbState extractState(ManagedBitSet mBitSet){
		BulbState bs = new BulbState();
		
		/**
		 * On, Bri, Hue, Sat, XY, CT, Alert, Effect, Transitiontime
		 */
		boolean[] propertiesFlags = new boolean[9];
		/** Get 9 bit properties flags **/		
		for (int j = 0; j < 9; j++) {
			propertiesFlags[j] = mBitSet.incrementingGet();
		}

		/** Get on bit **/
		bs.on = mBitSet.incrementingGet();
		
		/** Get 8 bit bri **/	
		if (propertiesFlags[1]) {
			bs.bri = mBitSet.extractNumber(8);
		}
	
		/** Get 16 bit hue **/
		if (propertiesFlags[2]) {
			bs.hue = mBitSet.extractNumber(16);
		}
		

		/** Get 8 bit sat **/
		if (propertiesFlags[3]) {
			bs.sat = (short) mBitSet.extractNumber(8);
		}
		
		/** Get 64 bit xy **/
		if (propertiesFlags[4]) {
			Double x = (double) Float.intBitsToFloat(mBitSet.extractNumber(32));
			Double y = (double) Float.intBitsToFloat(mBitSet.extractNumber(32));
			bs.xy = new Double[] { x, y };
		}
		
		/** Get 9 bit ct **/
		if (propertiesFlags[5]) {
			bs.ct = mBitSet.extractNumber(9);
		}
		
		/** Get 2 bit alert **/
		if (propertiesFlags[6]) {
			int value = mBitSet.extractNumber(2);
			switch (value) {
			case 0:
				bs.alert = "none";
				break;
			case 1:
				bs.alert = "select";
				break;
			case 2:
				bs.alert = "lselect";
				break;
			}
		}
		
		/** Get 4 bit effect **/
		// three more bits than needed, reserved for future API
		// functionality
		if (propertiesFlags[7]) {
			int value = mBitSet.extractNumber(4);
			switch (value) {
			case 0:
				bs.effect = "none";
				break;
			case 1:
				bs.effect = "colorloop";
				break;
			}
		}
		
		/** Get 16 bit transitiontime **/
		if (propertiesFlags[8]) {
			int value = mBitSet.extractNumber(16);
			bs.transitiontime = value;
		}
		
		return bs;
	}
	
	public static Pair<Integer[], Mood> decode(String code){
		Mood mood = new Mood();
		ArrayList<Integer> bList = new ArrayList<Integer>();
		ManagedBitSet mBitSet = new ManagedBitSet(code);
		
		//3 bit encoding version
		int encodingVersion = mBitSet.extractNumber(3);
		
		//1 bit optional bulb inclusion flags flag
		boolean hasBulbs = mBitSet.incrementingGet();
		if(hasBulbs){
			//50 bits of optional bulb inclusion flags
			for (int i = 0; i < 50; i++)
				if (mBitSet.incrementingGet())
					bList.add(i + 1);
		}
		
		if(encodingVersion == 1){
			int numChannels = mBitSet.extractNumber(6);
			mood.numChannels=numChannels;
			
			//1 bit timing addressing reference mode
			mood.timeAddressingRepeatPolicy = mBitSet.incrementingGet();
			
			//7 bit timing repeat number
			mood.setNumLoops(mBitSet.extractNumber(7));
			//flag infinite looping if max numLoops
			mood.setInfiniteLooping(mood.getNumLoops() == 127);
			
			//6 bit number of timestamps
			int numTimestamps = mBitSet.extractNumber(6);
			int[] timeArray = new int[numTimestamps];
			for(int i = 0; i<numTimestamps; i++){
				//20 bit timestamp
				timeArray[i]= mBitSet.extractNumber(20);
			}
			mood.usesTiming = !(timeArray.length==0||(timeArray.length==1&&timeArray[0]==0));
			
			//6 bit number of states
			int numStates = mBitSet.extractNumber(6);
			BulbState[] stateArray = new BulbState[numStates];
			for(int i = 0; i<numStates; i++){
				//decode each state
				stateArray[i] = extractState(mBitSet);
			}
			
			int numEvents = mBitSet.extractNumber(8);
			Event[] eList = new Event[numEvents];
			
			for(int i =0; i<numEvents; i++){
				Event e = new Event();
				e.channel = mBitSet.extractNumber(getBitLength(mood.numChannels));
				
				e.time = timeArray[mBitSet.extractNumber(getBitLength(numTimestamps))];
				
				e.state = stateArray[mBitSet.extractNumber(getBitLength(numStates))];
				
				eList[i] = e;
			}
			mood.events=eList;
			
		} if(encodingVersion==0){
			mBitSet.useLittleEndianEncoding(true);

			//7 bit number of states
			int numStates = mBitSet.extractNumber(7);
			BulbState[]	stateArray = new BulbState[numStates];

			/** Decode each state **/	
			for(int i = 0; i<numStates; i++){
				//decode each state
				stateArray[i] = extractState(mBitSet);
			}			
		}
		else{
			//TODO
			//Please update your app to open this mood
		}
		
		Integer[] bulbs=null;
		if(hasBulbs){
			bulbs = new Integer[bList.size()];
			for(int i = 0; i<bList.size(); i++)
				bulbs[i]=bList.get(i);
		}
				
		return new Pair<Integer[], Mood>(bulbs, mood);
	}
	
	public static String legacyEncode(Integer[] bulbS, BulbState[] bsRay) {
		//TODO replace
		return "";
	}

	public static Pair<Integer[], BulbState[]> legacyDecode(String encoded) {
		//TODO remove
		return null;
	}
}