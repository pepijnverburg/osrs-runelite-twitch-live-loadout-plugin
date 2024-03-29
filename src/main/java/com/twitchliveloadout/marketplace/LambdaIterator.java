package com.twitchliveloadout.marketplace;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class LambdaIterator {

	/**
	 * Handle all values in an hashmap with an iterator
	 */
	public static <KeyType, ValueType> void handleAllValues(ConcurrentHashMap<KeyType, ValueType> map, ValueHandler<ValueType> handler)
	{

		// guard: check if map is valid
		if (map == null)
		{
			return;
		}

		Collection<ValueType> values = map.values();
		handleAll(values, handler);
	}

	/**
	 * Handle all values in an hashmap with an iterator
	 */
	public static <KeyType, ValueType> void handleAll(Collection<ValueType> collection, ValueHandler<ValueType> handler)
	{
		// guard: check if collection is valid
		if (collection == null)
		{
			return;
		}

		Iterator<ValueType> iterator = collection.iterator();

		while (iterator.hasNext())
		{
			ValueType value = iterator.next();
			handler.execute(value);
		}
	}

	public interface ValueHandler<ValueType> {
		public void execute(ValueType value);
	}
}
