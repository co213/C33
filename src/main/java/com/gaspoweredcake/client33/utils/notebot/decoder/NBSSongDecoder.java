/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.notebot.decoder;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.gaspoweredcake.client33.utils.notebot.song.Note;
import com.gaspoweredcake.client33.utils.notebot.song.Song;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.jspecify.annotations.NonNull;

import java.io.*;

// https://github.com/koca2000/NoteBlockAPI/blob/master/src/main/java/com/xxmicloxx/NoteBlockAPI/utils/NBSDecoder.java

/**
 * Utils for reading Note Block Studio data
 *
 */
public class NBSSongDecoder extends SongDecoder {

    public static final int NOTE_OFFSET = 33; // Magic value (https://opennbs.org/nbs)

    /**
     * Parses a Song from a Note Block Studio project file (.nbs)
     *
     * @param songFile .nbs file
     * @return Song object representing a Note Block Studio project
     * @see Song
     */
    @Override
    @NonNull
    public Song parse(File songFile) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(songFile))) {
            return parse(in);
        }
    }

    /**
     * Parses a Song from an InputStream and a Note Block Studio project file (.nbs)
     *
     * @param inputStream of a .nbs file
     * @return Song object representing the given .nbs file
     * @see Song
     */
    @NonNull
    private Song parse(InputStream inputStream) throws Exception {
        Multimap<Integer, Note> notesMap = MultimapBuilder.linkedHashKeys().arrayListValues().build();

        DataInputStream dataInputStream = new DataInputStream(inputStream);
        int length = readShort(dataInputStream);
        int nbsversion = 0;
        if (length == 0) {
            nbsversion = dataInputStream.readByte();
            dataInputStream.readByte(); // first custom instrument
            if (nbsversion >= 3) {
                length = readShort(dataInputStream);
            }
        }
        readShort(dataInputStream); // Song Height
        String title = readString(dataInputStream);
        String author = readString(dataInputStream);
        readString(dataInputStream); // original author
        readString(dataInputStream); // description
        float speed = readShort(dataInputStream) / 100f;
        if (speed <= 0) throw new IOException("Song tempo must be positive.");
        dataInputStream.readBoolean(); // auto-save
        dataInputStream.readByte(); // auto-save duration
        dataInputStream.readByte(); // x/4ths, time signature
        readInt(dataInputStream); // minutes spent on project
        readInt(dataInputStream); // left clicks (why?)
        readInt(dataInputStream); // right clicks (why?)
        readInt(dataInputStream); // blocks added
        readInt(dataInputStream); // blocks removed
        readString(dataInputStream); // .mid/.schematic file name
        if (nbsversion >= 4) {
            dataInputStream.readByte(); // loop on/off
            dataInputStream.readByte(); // max loop count
            readShort(dataInputStream); // loop start tick
        }

        int sourceTick = -1;
        while (true) {
            int jumpTicks = readShort(dataInputStream); // jumps till next tick
            if (jumpTicks == 0) {
                break;
            }
            sourceTick = Math.addExact(sourceTick, jumpTicks);
            double tick = sourceTick * (20.0 / speed);
            if (tick > Integer.MAX_VALUE) throw new IOException("Song duration exceeds supported ticks.");
            while (true) {
                int jumpLayers = readShort(dataInputStream); // jumps till next layer
                if (jumpLayers == 0) {
                    break;
                }
                byte instrument = dataInputStream.readByte();

                byte key = dataInputStream.readByte();
                if (nbsversion >= 4) {
                    dataInputStream.readUnsignedByte(); // note block velocity
                    dataInputStream.readUnsignedByte(); // note panning, 0 is right in nbs format
                    readShort(dataInputStream); // note block pitch
                }

                NoteBlockInstrument inst = fromNBSInstrument(instrument);

                // Probably a custom instrument. Ignore this note
                if (inst == null) continue;

                Note note = new Note(inst /* instrument */, key - NOTE_OFFSET /* note */);
                setNote((int) Math.round(tick), note, notesMap);
            }
        }

        return new Song(notesMap, title, author);
    }

    /**
     * Sets a note at a tick in a song
     */
    private static void setNote(int ticks, Note note, Multimap<Integer, Note> notesMap) {
        notesMap.put(ticks, note);
    }

    private static int readShort(DataInputStream dataInputStream) throws IOException {
        int byte1 = dataInputStream.readUnsignedByte();
        int byte2 = dataInputStream.readUnsignedByte();
        return byte1 + (byte2 << 8);
    }

    private static int readInt(DataInputStream dataInputStream) throws IOException {
        int byte1 = dataInputStream.readUnsignedByte();
        int byte2 = dataInputStream.readUnsignedByte();
        int byte3 = dataInputStream.readUnsignedByte();
        int byte4 = dataInputStream.readUnsignedByte();
        return (byte1 + (byte2 << 8) + (byte3 << 16) + (byte4 << 24));
    }

    private static String readString(DataInputStream dataInputStream) throws IOException {
        int length = readInt(dataInputStream);
        if (length < 0) {
            throw new EOFException("Length can't be negative! Length: " + length);
        }
        if (length > dataInputStream.available()) {
            throw new EOFException("Can't read string that is larger than a buffer! Length: " + length + " Readable Bytes Length: " + dataInputStream.available());
        }

        StringBuilder builder = new StringBuilder(length);
        for (; length > 0; --length) {
            char c = (char) dataInputStream.readUnsignedByte();
            if (c == (char) 0x0D) {
                c = ' ';
            }
            builder.append(c);
        }
        return builder.toString();
    }

    // Magic Values (https://opennbs.org/nbs)
    private static NoteBlockInstrument fromNBSInstrument(int instrument) {
        return switch (instrument) {
            case 0 -> NoteBlockInstrument.HARP;
            case 1 -> NoteBlockInstrument.BASS;
            case 2 -> NoteBlockInstrument.BASEDRUM;
            case 3 -> NoteBlockInstrument.SNARE;
            case 4 -> NoteBlockInstrument.HAT;
            case 5 -> NoteBlockInstrument.GUITAR;
            case 6 -> NoteBlockInstrument.FLUTE;
            case 7 -> NoteBlockInstrument.BELL;
            case 8 -> NoteBlockInstrument.CHIME;
            case 9 -> NoteBlockInstrument.XYLOPHONE;
            case 10 -> NoteBlockInstrument.IRON_XYLOPHONE;
            case 11 -> NoteBlockInstrument.COW_BELL;
            case 12 -> NoteBlockInstrument.DIDGERIDOO;
            case 13 -> NoteBlockInstrument.BIT;
            case 14 -> NoteBlockInstrument.BANJO;
            case 15 -> NoteBlockInstrument.PLING;
            default -> null;
        };
    }

}
