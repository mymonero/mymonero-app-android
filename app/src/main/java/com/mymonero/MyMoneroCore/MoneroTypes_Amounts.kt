//
//  MoneroTypes_Amounts.kt
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

package com.mymonero.MyMoneroCore

import com.mymonero.Currencies.Currency
import com.mymonero.Currencies.MoneyAmountFormatters
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow

typealias MoneroAmount = BigInteger

object MoneroAmounts
{
	val atomicUnitsConversionFactor = 10.0.pow(MoneroConstants.currency_unitPlaces)
	val atomicUnitsConversionFactor_bigDecimal = BigDecimal(atomicUnitsConversionFactor)
}

fun DoubleFromMoneroAmount(amount: MoneroAmount): Double
{
	return amount.toDouble()/ MoneroAmounts.atomicUnitsConversionFactor
}
fun FormattedString(fromMoneroAmount: MoneroAmount): String
{
	return DoubleFromMoneroAmount(fromMoneroAmount).toString() // TODO: does this really work for all cases?
}
fun MoneroAmountFrom(doubleValue: Double): MoneroAmount
{
	//
	// TODO: using BigDecimal is novel here - confirm this is working properly
	//
	val bigDecimal = BigDecimal(doubleValue) * MoneroAmounts.atomicUnitsConversionFactor_bigDecimal
	//
	return bigDecimal.toBigInteger() // toBigIntegerExact() is probably desirable here for rigor, but as it throws an exception when precision necessary, I need to ensure that would be totally precluded at the UI level first
}
fun MoneroAmountFrom( // aka cn_util.parse_money(...)
	moneroAmountDoubleString: String,
	decimalSeparator: String = Currency.decimalSeparator/* seems ok to reach into this namespace here */
): MoneroAmount {
	//
	// TODO: Using a parser in bigDecimal mode and converting to bigInteger is novel here - confirm this is working properly
	//
	val bigDecimal = MoneroAmounts.atomicUnitsConversionFactor_bigDecimal * MoneyAmountFormatters.localized_bigDecimalParsing_doubleFormatter.parse(moneroAmountDoubleString) as BigDecimal
	//
	return bigDecimal.toBigInteger() as MoneroAmount // toBigIntegerExact() might be desirable here for rigor, but as it throws an exception when precision necessary, I need to ensure that would be totally precluded at the UI level first
}