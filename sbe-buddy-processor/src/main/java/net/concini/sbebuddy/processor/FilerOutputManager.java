package net.concini.sbebuddy.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

import org.agrona.generation.DynamicPackageOutputManager;

/**
 * sbe-tool's output over the {@link Filer}: one source file per name under the
 * package it was told, originating from the schema package. A second open of a
 * name, which the header flyweight does, gets a writer that discards, because
 * the {@code Filer} cannot recreate a file.
 */
final class FilerOutputManager implements DynamicPackageOutputManager {

	private final Filer filer;
	private final Element originating;
	private final Set<String> opened = new HashSet<>();
	private String packageName = "";

	FilerOutputManager(Filer filer, Element originating) {
		this.filer = filer;
		this.originating = originating;
	}

	@Override
	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	@Override
	public Writer createOutput(String name) throws IOException {
		String qualified = packageName + "." + name;
		if (!opened.add(qualified)) {
			return Writer.nullWriter();
		}
		return filer.createSourceFile(qualified, originating).openWriter();
	}
}
