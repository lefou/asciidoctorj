package org.asciidoctor.ast.impl;

import java.util.Arrays;
import java.util.List;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.impl.StructuralNodeImpl;
import org.jruby.RubyArray;
import org.jruby.runtime.builtin.IRubyObject;

public class BlockImpl extends StructuralNodeImpl implements Block {

    public BlockImpl(IRubyObject blockDelegate) {
        super(blockDelegate);
    }

    @Override
    public List<String> lines() {
        return getLines();
    }

    @Override
    public List<String> getLines() {
        return getList("lines", String.class);
    }

    @Override
    public void setLines(List<String> lines) {
        RubyArray newLines = getRuntime().newArray(lines.size());
        for (String s: lines) {
            newLines.add(getRuntime().newString(s));
        }
        setRubyProperty("lines", newLines);
    }

    @Override
    public String source() {
        return getSource();
    }

    @Override
    public String getSource() {
        return getString("source");
    }

    @Override
    public void setSource(String source) {
        setLines(Arrays.asList(source.split("\n")));
    }
}
