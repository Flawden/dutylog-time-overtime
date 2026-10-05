package ru.daniil.shifts.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Article153RemunerationPricingTest {
    PayPricingEngine engine=new PayPricingEngine();
    @ParameterizedTest @CsvSource({"10000,9600,60,10000,63","1,60,30,10000,1","1,60,29,10000,0","500,60,60,15000,750","500,60,60,0,0","500,60,0,10000,0"})
    void exactReviewedPeriodFormula(long amount,long period,int minutes,int bps,long expected){assertEquals(expected,engine.pricePeriodPremium(amount,period,minutes,bps));}
    @ParameterizedTest @CsvSource({"0,60,60,10000","-1,60,60,10000","1,0,60,10000","1,-1,60,10000","1,60,-1,10000","1,60,60,-1"})
    void invalidInputs(long amount,long period,int minutes,int bps){assertThrows(IllegalArgumentException.class,()->engine.pricePeriodPremium(amount,period,minutes,bps));}
    @Test void overflowCannotWrapToNegative(){assertThrows(IllegalArgumentException.class,()->engine.pricePeriodPremium(Long.MAX_VALUE,1,Integer.MAX_VALUE,Integer.MAX_VALUE));}
    @Test void hugePeriodDenominatorDoesNotOverflow(){assertEquals(1,engine.pricePeriodPremium(Long.MAX_VALUE,Long.MAX_VALUE,1,10000));}
    @Test void hourPathExactlyMatchesExistingPremiumPricing(){for(int minutes:new int[]{1,29,30,60,1000})for(int bps:new int[]{0,2500,10000,15000})assertEquals(engine.price(12345,List.of(new PayPricingEngine.PricingSlice(minutes,List.of(new PayPricingEngine.PremiumComponent("X",bps))))).premiumAmountMinor(),engine.pricePeriodPremium(12345,60,minutes,bps));}
    @Test void independentIntegerRationalOracle(){var random=new Random(89);for(int i=0;i<500;i++){long amount=1+random.nextInt(10000000),period=1+random.nextInt(46080);int minutes=random.nextInt(46080),bps=random.nextInt(100000);var numerator=BigInteger.valueOf(amount).multiply(BigInteger.valueOf(minutes)).multiply(BigInteger.valueOf(bps));var denominator=BigInteger.valueOf(period).multiply(BigInteger.valueOf(10000));long expected=numerator.multiply(BigInteger.TWO).add(denominator).divide(denominator.multiply(BigInteger.TWO)).longValueExact();assertEquals(expected,engine.pricePeriodPremium(amount,period,minutes,bps));}}
}
