package com.rx201.apkmon.demo;
import com.rx201.apkmon.demo.IAddResultCallback;

// Adder service interface.
interface IMyService {
int add( in int i1, in int i2, IAddResultCallback ResultCallback );
}
