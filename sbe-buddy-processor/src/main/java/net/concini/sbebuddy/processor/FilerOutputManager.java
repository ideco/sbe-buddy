package net.concini.sbebuddy.processor;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

import org.agrona.generation.DynamicPackageOutputManager;

/**
 * The generator's output over the {@link Filer}: one source file per name under
 * the package it was told, originating from the schema package. The generator
 * hands over each name once and only after the whole generation succeeded,
 * which is what the {@code Filer}, unable to recreate a file, needs.
 */
final class FilerOutputManager implements DynamicPackageOutputManager {

	private final Filer filer;
	private final Element originating;
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
		return filer.createSourceFile(packageName + "." + name, originating).openWriter();
	}
}
