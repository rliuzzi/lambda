package org.example.lambda;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class TargetTest extends TestCase {
	/**
	 * Create the test case
	 *
	 * @param testName
	 *            name of the test case
	 */
	public TargetTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(TargetTest.class);
	}

	/**
	 * Rigourous Test :-)
	 */
	public void testApp() {
		
		String srcKey = "101879327/segula tecnologias/9356324566.gif";
		String rightDestKey = "9356324566";

		String dstKey = srcKey.substring(
				srcKey.lastIndexOf("/") > 0 ? srcKey.lastIndexOf("/") + 1 : 0,
				srcKey.lastIndexOf(".") > 0 ? srcKey.lastIndexOf(".") : srcKey
		
						.length() - 1);
		assertEquals(dstKey, rightDestKey);
	}
}
