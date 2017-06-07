package com.headstrongpro.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * HeadstrongLoader
 * <p>
 * <p>
 * Created by rajmu on 17.06.07.
 */
public class UnzipUtilityTest {
    @Test
    public void unzip() throws Exception {
        UnzipUtility unzipUtility = new UnzipUtility();
        unzipUtility.unzip("C:/Users/rajmu/Desktop/test.zip", "C:/Users/rajmu/Desktop/dab/");
    }

}