//
//  PasswordController.kt
//  MyMonero
//
//  Copyright (c) 2014-2018, MyMonero.com
//
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without modification, are
//  permitted provided that the following conditions are met:
//
//  1. Redistributions of source code must retain the above copyright notice, this list of
//	conditions and the following disclaimer.
//
//  2. Redistributions in binary form must reproduce the above copyright notice, this list
//	of conditions and the following disclaimer in the documentation and/or other
//	materials provided with the distribution.
//
//  3. Neither the name of the copyright holder nor the names of its contributors may be
//	used to endorse or promote products derived from this software without specific
//	prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
//  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
//  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
//  THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
//  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
//  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
//  STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
//  THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//
package com.mymonero.Application

import android.content.Context
import com.mymonero.KotlinUtils.BuiltDependency
import junit.framework.Assert

class AppLib_Bridge: BuiltDependency
{
	//
	// Lifecycle - Class
	companion object
	{
		init {
			System.loadLibrary("mymonero_app_jni_bridge")
		}
	}
	//
	// Properties - Instance
	private lateinit var applicationContext: Context
	//
	// Lifecycle - Instance
	constructor() {}
	fun init_applicationContext(dep: Context)
	{
		this.applicationContext = dep
	}
	override fun setup()
	{
		if (this.applicationContext == null) {
			throw AssertionError("Missing dependencies")
		}
		// waiting until setup to do this in case any deps must be injected
		this.initLib(
			documentsPath = this.applicationContext.filesDir.absolutePath
		)
	}
	//
	// Accessors
	external fun stringFromJNI(): String
	//
	// Imperatives
	external fun initLib(documentsPath: String)
}