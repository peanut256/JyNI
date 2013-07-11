/*
 * Copyright of Python and Jython:
 * Copyright (c) 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010,
 * 2011, 2012, 2013 Python Software Foundation.  All rights reserved.
 * 
 * Copyright of JyNI:
 * Copyright (c) 2013 Stefan Richthofer.  All rights reserved.
 *
 *
 * This file is part of JyNI.
 *
 * JyNI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JyNI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JyNI.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 */


package JyNI;

import org.python.core.*;

import java.lang.reflect.Field;
import java.util.*;

public class JyNI {
	static {
		String dir = System.getProperty("user.dir");
		dir = dir.substring(0, dir.lastIndexOf('/'));
		System.load(dir+"/JyNI-Loader/Debug/libJyNI-Loader.so");
		initJyNI(dir+"/JyNI-C/Debug/libJyNI.so");
	}
	
	public static final String JyNIHandleAttr = "_JyNIHandleAttr".intern();
	
	/*We simply trust that they won't change this value.
	Why should they? It would only issue binary compatibility.
	 */
	/**Lazy function call binding.*/
	public static final int RTLD_LAZY = 0x00001;
	/**Immediate function call binding.*/
	public static final int RTLD_NOW = 0x00002;
	/**Mask of binding time value.*/
	public static final int RTLD_BINDING_MASK = 0x3;
	/**Do not load the object.*/
	public static final int RTLD_NOLOAD = 0x00004;
	/**Use deep binding.*/
	public static final int RTLD_DEEPBIND = 0x00008;

	/**If the following bit is set in the MODE argument to `dlopen',
	   the symbols of the loaded object and its dependencies are made
	   visible as if the object were linked directly into the program.*/
	public static final int RTLD_GLOBAL = 0x00100;

	/**Unix98 demands the following flag which is the inverse to RTLD_GLOBAL.
	   The implementation does this by default and so we can define the
	   value to zero.*/
	public static final int RTLD_LOCAL = 0;

	/** Do not delete object when closed.*/
	public static final int RTLD_NODELETE = 0x01000;
	
	public static final int RTLD_JyNI_DEFAULT = RTLD_LAZY | RTLD_GLOBAL;//RTLD_NOW;
	
	//protected static HashMap<PyObject, JyObject> jyObjects = new HashMap();
	//protected static HashMap<PyObject, Long> nativeHandles = new HashMap<PyObject, Long>();
	protected static HashMap<Long, PyObject> CPeerHandles = new HashMap<Long, PyObject>();
	
	public static native void initJyNI(String JyNILibPath);
	public static native void clearPyCPeer(long objectHandle, long refHandle);
	public static native PyModule loadModule(String moduleName, String modulePath);
	//public static native JyObject callModuleFunctionGlobalReferenceMode(CPythonModule module, String name, JyObject self, JyObject... args);
	//public static native PyObject callModuleFunctionGlobalReferenceMode(JyNIModule module, String name, PyObject self, long selfNativeHandle, PyObject[] args, long[] handles);
	//public static native PyObject callModuleFunctionLocalReferenceMode(JyNIModule module, String name, PyObject self, long selfNativeHandle, PyObject... args);
	public static native PyObject callPyCPeer(long peerHandle, PyObject args, PyObject kw);
	public static native PyObject getAttrString(long peerHandle, String name);
	public static native int setAttrString(long peerHandle, String name, PyObject value);
	public static native PyObject repr(long peerHandle);
	public static native String PyObjectAsString(long peerHandle);
	public static native PyString PyObjectAsPyString(long peerHandle);
	
	//List-Stuff:
	public static native PyObject JyList_get(long handle, int index);
	public static native int JyList_size(long handle);
	public static native PyObject JyList_set(long handle, int index, PyObject o, long pyObject);
	public static native void JyList_add(long handle, int index, PyObject o, long pyObject);
	public static native PyObject JyList_remove(long handle, int index);
	
//	public static PyObject callModuleFunction(JyNIModule module, String name, PyObject self, long selfNativeHandle, PyObject... args)
//	{
//		return callModuleFunctionLocalReferenceMode(module, name, self, selfNativeHandle, args);
//	}
	
	public static PyObject getPyObjectByName(String name)
	{
		//todo: Check, whether this does what it is supposed to
		//System.out.println("JyNI: Getting Object by name: "+name);
		PySystemState sysState = Py.getSystemState();
		return Py.getThreadState().systemState.__dict__.__getitem__(new PyString(name))._doget(sysState);
		//PyObject er = Py.getThreadState().systemState.__dict__.__getitem__(new PyString(name));
		/*PyObject er = sysState.__dict__.__finditem__(name);
		System.out.println("found: "+er.getClass().getName());
		System.out.println(er.getType());
		System.out.println(er.getType().getClass().getName());
		System.out.println(er.getType().getName());
		PyObject er2 = er._doget(sysState);
		System.out.println(er2);
		System.out.println(er2.getClass());
		System.out.println(er2.getType());
		return er;*/
	}
	
	public static long getJyObjectByName(String name)
	{
		//System.out.println("getJyObjectByName "+name);
		PyObject obj = getPyObjectByName(name);
		//System.out.println("obj: "+obj);
		//Long er = nativeHandles.get(obj);
		//System.out.println("nativeHandles: "+nativeHandles);
		//System.out.println("found: "+er);
		//return er != null ? er : 0;
		return lookupNativeHandle(obj);
	}
	
	public static int getDLVerbose()
	{
		//current hard-coded Debug:
		return 0;
		
		//real implementation:
		/*switch (Options.verbose)
		case Py.DEBUG: return true;
		case Py.COMMENT: return true;
		case Py.MESSAGE: return true;
		default: return false;*/
	}
	
	public static int getDLOpenFlags()
	{
		try {
			return ((PyInteger) Py.getThreadState().systemState.__getattr__("dlopenflags")).asInt();
		} catch (Exception e1)
		{
			try {
				return ((PyInteger) Py.getSystemState().__getattr__("dlopenflags")).asInt();
			} catch (Exception e2)
			{
				return RTLD_JyNI_DEFAULT;
			}
		}
	}
		
	public static void setDLOpenFlags(int n)
	{
		try {
			Py.getThreadState().systemState.__setattr__("dlopenflags", new PyInteger(n));
		} catch (Exception e1)
		{
			try {
				Py.getSystemState().__setattr__("dlopenflags", new PyInteger(n));
			} catch (Exception e2)
			{
				return;
			}
		}
	}
	
	public static void setNativeHandle(PyObject object, long handle)
	{
		//no WeakReferences needed here, because clearNativeHandle is always calles
		//when a corresponding PyObject on C-Side is deallocated
		//nativeHandles.put(object, handle);
		if (!(object instanceof PyCPeer))
		{
			//PyCPeer peer = (PyCPeer) object.__getattr__(JyNIHandleAttr);
			//if (peer == null)
			object.__setattr__(JyNIHandleAttr, new PyCPeer(handle, object.getType()));
		}
	}
	
	//public static long lookupNativeHandle(PyObject object)
	public static long lookupNativeHandle(PyObject object)
	{
		//System.out.println("lookup native handle: "+object);
		if (object == null) return 0;
		if (object instanceof PyCPeer) return ((PyCPeer) object).objectHandle;
		else {
//			System.out.println("Exception before:");
//			System.out.println(Py.getThreadState().exception);
			PyCPeer peer = (PyCPeer) object.__findattr__(JyNIHandleAttr);
//			System.out.println("Exception afterwards:");
//			System.out.println(Py.getThreadState().exception);
//			System.out.println("retrieved: "+peer);
			return peer == null ? 0 : ((PyCPeer) peer).objectHandle;
		}
		//Problem: When JyNI is initialized, nativeHandles have not been initialized...
		/*try
		{
			Long er = nativeHandles.get(object);
			return er == null ? 0 : er;
		} catch (Exception e)
		{
			System.out.println("ERRR: "+e);
			//System.out.println(nativeHandles);
			System.out.println(object);
		}
		return 0;*/
	}
	
	public static PyObject lookupCPeerHandle(long handle)
	{
		//Problem: When JyNI is initialized, nativeHandles have not been initialized...
		if (handle == 0) return null;
		else return CPeerHandles.get(handle);
	}
	
	public static void clearNativeHandle(PyObject object)
	{
		//nativeHandles.remove(object);
		PyCPeer peer = (PyCPeer) object.__findattr__(JyNIHandleAttr);
		if (peer != null)
		{
			peer.objectHandle = 0;
			object.__delattr__(JyNIHandleAttr);
		}
	}
	
//	public static PyObject callModuleFunctionGlobalReferenceMode(JyNIModule module, String name, PyObject self, PyObject... args)
//	{
//		Long selfHandle = nativeHandles.get(self);
//		
//		long[] handles = new long[args.length];
//		for (int i = 0; i < args.length; ++i)
//		{
//			Long handle = nativeHandles.get(args[i]);
//			handles[i] = handle == null ? 0 : handle;
//		}
//		return callModuleFunctionGlobalReferenceMode(module, name, self, selfHandle == null ? 0 : selfHandle, args, handles);
//	}
	
	public static PyObject _PyImport_FindExtension(String name, String filename)
	{
		return null;
	}
	
	public static PyObject PyImport_GetModuleDict()
	{
		return Py.getSystemState().modules;
	}
	
	public static PyObject PyImport_AddModule(String name)
	{
		String nm = name.intern();
		PySystemState pss = Py.getSystemState();
		PyObject er = pss.modules.__finditem__(name);
		if (er != null && er.getType().isSubType(PyModule.TYPE)) return er;
		else
		{
			er = new PyModule(nm);
			//pss.modules.__setattr__(nm, er);
			pss.modules.__setitem__(name, er);
			//System.out.println("JYNY rr: "+er);
			//System.out.println(er.getType().getName());
			//ERRR
			return er;
		}
	}
	
	public static PyObject JyNI_GetModule(String name)
	{
		String nm = name.intern();
		PySystemState pss = Py.getSystemState();
		PyObject er = pss.modules.__finditem__(name);
		if (er != null && er.getType().isSubType(PyModule.TYPE)) return er;
		else
		{
			System.out.println("No module found: "+name);
			return null;
		}
	}
	
	public static PyType getPyType(Class pyClass)
	{
		try {
			Field tp = pyClass.getField("TYPE");
			PyType t = (PyType) tp.get(null);
			//System.out.println(t.getName());
			return t;
		} catch (Exception e) {return null;}
	}
	
	public static long[] getNativeAvailableKeysAndValues(PyDictionary dict)
	{
		Map<PyObject, PyObject> map = dict.getMap();
		Iterator<PyObject> it = map.keySet().iterator();
		long l;
		Vector<Long> er = new Vector<Long>();
		while (it.hasNext())
		{
			//l = nativeHandles.get(it.next());
			//if (l != null) er.add(l);
			l = lookupNativeHandle(it.next());
			if (l != 0 ) er.add(l);
		}
		it = map.values().iterator();
		while (it.hasNext())
		{
			//l = nativeHandles.get(it.next());
			//if (l != null) er.add(l);
			l = lookupNativeHandle(it.next());
			if (l != 0 ) er.add(l);
		}
		long[] er2 = new long[er.size()];
		for (int i = 0; i < er2.length; ++i)
			er2[i] = er.get(i).longValue();
		return er2;
	}
	
	protected static int lastIndex;
	protected static PyDictionary lastDict;
	protected static Iterator<Map.Entry<PyObject, PyObject>> dictIterator;
	public static synchronized JyNIDictNextResult getPyDictionary_Next(PyDictionary dict, int index)
	{
		if (dict == lastDict && index == lastIndex && dictIterator != null)
		{
			Map.Entry<PyObject, PyObject> me;
			PyObject value;
			while (dictIterator.hasNext())
			{
				++lastIndex;
				me = dictIterator.next();
				value = me.getValue();
				if (value != null)
				{
					if (lastIndex == dict.size()) lastIndex = -lastIndex;
					return new JyNIDictNextResult(lastIndex, me.getKey(), value);
				}
			}
		} else
		{
			lastDict = dict;
			dictIterator = dict.getMap().entrySet().iterator();
			lastIndex = 0;
			Map.Entry<PyObject, PyObject> me;
			while (lastIndex < index)
			{
				++lastIndex;
				if (!dictIterator.hasNext()) return null;
				else dictIterator.next();
			}
			PyObject value;
			while (dictIterator.hasNext())
			{
				++lastIndex;
				me = dictIterator.next();
				value = me.getValue();
				if (value != null)
				{
					if (lastIndex == dict.size()) lastIndex = -lastIndex;
					return new JyNIDictNextResult(lastIndex, me.getKey(), value);
				}
			}
		}
		return null;
	}
	
	public static void printPyLong(PyObject pl)
	{
		System.out.println("printPyLong");
		System.out.println(((PyLong) pl).getValue());
	}
	
	/*public static JyNIDictNextResult getPyDictionary_Next(PyDictionary dict, int index)
	{
		if (index >= dict.size()) return null;
		Object[] ar = dict.entrySet().toArray();
		int nxt = index;
		Map.Entry me = (Map.Entry) ar[nxt++];
		while(me.getValue() == null)
		{
			if (nxt >= ar.length) return null;
			me = (Map.Entry) ar[nxt++];
		}
		if (nxt == ar.length) nxt = -1;
		return new JyNIDictNextResult(nxt, (PyObject) me.getKey(), (PyObject) me.getValue());
	}*/
	
	/*
	public static PyObject constructDefaultObject(Class pyObjectClass) throws IllegalArgumentException
	{
		if (pyObjectClass.equals(PyType.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyNotImplemented.class)) return Py.NotImplemented;
		else if (pyObjectClass.equals(PyNone.class)) return Py.None;
		else if (pyObjectClass.equals(PyFile.class)) return new PyFile();
		else if (pyObjectClass.equals(PyModule.class)) return new PyModule();
		else if (pyObjectClass.equals(PyCell.class)) return new PyCell();
		else if (pyObjectClass.equals(PyClass.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyInstance.class)) return new PyInstance();
		else if (pyObjectClass.equals(PyMethod.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyFunction.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyClassMethod.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyStaticMethod.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyMethodDescr.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyClassMethodDescr.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyDictProxy.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyProperty.class)) return new PyProperty();
		else if (pyObjectClass.equals(PyBoolean.class)) return new PyBoolean(false);
		else if (pyObjectClass.equals(PyFloat.class)) return new PyFloat(0.0f);
		else if (pyObjectClass.equals(PyInteger.class)) return new PyInteger(0);
		else if (pyObjectClass.equals(PyLong.class)) return new PyLong(0);
		else if (pyObjectClass.equals(PyComplex.class)) return new PyComplex(0, 0);
		else if (pyObjectClass.equals(PyUnicode.class)) return new PyUnicode();
		else if (pyObjectClass.equals(PyString.class)) return new PyString();
		else if (pyObjectClass.equals(PyBaseString.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" is considered an abstract class.");
		else if (pyObjectClass.equals(PySequenceIter.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyXRange.class)) return new PyXRange(0);
		else if (pyObjectClass.equals(PyTuple.class)) return new PyTuple();
		else if (pyObjectClass.equals(PyFastSequenceIter.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyList.class)) return new PyList();
		else if (pyObjectClass.equals(PyReversedIterator.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyDictionary.class)) return new PyDictionary();
		else if (pyObjectClass.equals(PySet.class)) return new PySet();
		else if (pyObjectClass.equals(PyFrozenSet.class)) return new PyFrozenSet();
		else if (pyObjectClass.equals(PyEnumerate.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PySlice.class)) return new PySlice();
		else if (pyObjectClass.equals(PyEllipsis.class)) return Py.Ellipsis;
		else if (pyObjectClass.equals(PyGenerator.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyCode.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyCallIter.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyFrame.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PySuper.class)) return new PySuper();
		else if (pyObjectClass.equals(PyBaseException.class)) return new PyBaseException();
		else if (pyObjectClass.equals(PyTraceback.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" cannot be constructed this way.");
		else if (pyObjectClass.equals(PyByteArray.class)) return new PyByteArray();
		else if (pyObjectClass.equals(PyObject.class)) throw new IllegalArgumentException(pyObjectClass.getName()+" is considered an abstract class.");
		else throw new IllegalArgumentException("Given class refers to no PyObject known by JNI");
	}*/
	
	/*public static PyObject callModuleFunction(CPythonModule module, String name, PyObject self, PyObject... args)
	{
		JyObject[] jyArgs = new JyObject[args.length];
		for (int i = 0; i < jyArgs.length; ++i)
		{
			jyArgs[i] = jyObjects.get(args[i]);
			if (jyArgs[i] == null)
			{
				jyArgs[i] = new JyObject(args[i]);
				jyObjects.put(args[i], jyArgs[i]);
			}
		}
		if (self != null)
		{
			JyObject self2 = jyObjects.get(self);
			if (self2 == null)
			{
				self2 = new JyObject(self);
				jyObjects.put(self, self2);
			}
			JyObject er = callModuleFunction(module, name, self2, jyArgs);
			jyObjects.put(er.object, er);
			return er.object;
		} else
		{
			JyObject er = callModuleFunction(module, name, null, jyArgs);
			jyObjects.put(er.object, er);
			return er.object;
		}
	}*/
	
	public static long[] lookupNativeHandles(PyList lst)
	{
		PyObject[] obj = lst.getArray();
		long[] er = new long[obj.length];
		for (int i = 0; i < er.length; ++i)
			er[i] = lookupNativeHandle(obj[i]);
		return er;
	}
	
	//--------------errors-section-----------------
	public static PyObject exceptionByName(String name)
	{
		System.out.println("look for exception: "+name);
		String rawName = name;
		int pin = name.indexOf('.');
		if (pin != -1) rawName = rawName.substring(pin+1);
		System.out.println("rawName: "+rawName);
		try {
			Field exc = Py.class.getField(rawName);
			PyObject er = (PyObject) exc.get(null);
			System.out.println("return "+er);
			System.out.println("class: "+er.getClass());
			return er;
		} catch (Exception e)
		{
			System.out.println("Could not obtain Exception: "+name);
			System.out.println("Reason: "+e);
			return null;
		}
	}
	
	public static void PyErr_Restore(PyObject type, PyObject value, PyObject traceback)
	{
		ThreadState tstate = Py.getThreadState();
		tstate.exception = traceback instanceof PyTraceback ? new PyException(type, value, (PyTraceback) traceback) : new PyException(type, value);
	}
	
	public static void PyErr_Clear()
	{
		ThreadState tstate = Py.getThreadState();
		tstate.exception = null;
	}
	
	public static PyException PyErr_Fetch()
	{
		ThreadState tstate = Py.getThreadState();

		PyException er = tstate.exception;
		PyErr_Clear();
		return er;
	}
	
	public static PyObject PyErr_Occurred()
	{
		ThreadState tstate = Py.getThreadState();
		/*if (tstate.exception != null)
		{
			System.out.println("PyErr_Occurred: "+tstate.exception.getMessage());
			System.out.println("type "+tstate.exception.type);
		}*/
		return tstate.exception == null ? null : tstate.exception.type;
	}
	
	public static boolean PyErr_ExceptionMatches(PyObject exc)
	{
		ThreadState tstate = Py.getThreadState();
		return tstate.exception == null ? exc == null : tstate.exception.match(exc);
	}
	
	public static void PyErr_SetObject(PyObject exception, PyObject value)
	{
		PyErr_Restore(exception, value, null);
	}
	
	public static void PyErr_SetString(PyObject exc, String value)
	{
		PyErr_Restore(exc, Py.newString(value), null);
	}
	
	public static void PyErr_SetNone(PyObject exc)
	{
		PyErr_Restore(exc, null, null);
	}
	
	public static PyObject PyErr_NoMemory()
	{
		if (PyErr_ExceptionMatches(Py.MemoryError))
			// already current
			return null;

		// raise the pre-allocated instance if it still exists
		/*if (PyExc_MemoryErrorInst)
			PyErr_SetObject(PyExc_MemoryError, PyExc_MemoryErrorInst);
		else
			// this will probably fail since there's no memory and hee,
			// hee, we have to instantiate this class*/

		PyErr_SetNone(Py.MemoryError);

		return null;
	}
		
	public static void PyErr_WriteUnraisable(PyObject obj)
	{
		//Todo: Create and use something like JyNIUnraisableError instead of UnknownError.
		Py.writeUnraisable(new UnknownError("JyNI caused unraisable exception"), obj);
		
		//System.err.println("WriteUnraisableException:");
		//System.err.println(obj);
		//System.err.println(PyErr_Fetch());
		/*PyObject *f, *t, *v, *tb;
		PyErr_Fetch(&t, &v, &tb);
		f = PySys_GetObject("stderr");
		if (f != NULL) {
			PyFile_WriteString("Exception ", f);
			if (t) {
				PyObject* moduleName;
				char* className;
				assert(PyExceptionClass_Check(t));
				className = PyExceptionClass_Name(t);
				if (className != NULL) {
					char *dot = strrchr(className, '.');
					if (dot != NULL)
						className = dot+1;
				}

				moduleName = PyObject_GetAttrString(t, "__module__");
				if (moduleName == NULL)
					PyFile_WriteString("<unknown>", f);
				else {
					char* modstr = PyString_AsString(moduleName);
					if (modstr &&
						strcmp(modstr, "exceptions") != 0)
					{
						PyFile_WriteString(modstr, f);
						PyFile_WriteString(".", f);
					}
				}
				if (className == NULL)
					PyFile_WriteString("<unknown>", f);
				else
					PyFile_WriteString(className, f);
				if (v && v != Py_None) {
					PyFile_WriteString(": ", f);
					PyFile_WriteObject(v, f, 0);
				}
				Py_XDECREF(moduleName);
			}
			PyFile_WriteString(" in ", f);
			PyFile_WriteObject(obj, f, 0);
			PyFile_WriteString(" ignored\n", f);
			PyErr_Clear(); // Just in case
		}
		Py_XDECREF(t);
		Py_XDECREF(v);
		Py_XDECREF(tb);*/
	}
	
	/*public static void main(String[] args)
	{
		System.out.println(System.getProperty("user.dir"));
	}
	/*
		Class c = PyBoolean.class;
		try {
			Field tp = c.getField("TYPE");
			PyType t = (PyType) tp.get(null);
			System.out.println(t.getName());
		} catch (Exception e) {}
	}*/
	
	public static int slice_compare(PySlice v, PySlice w)
	{
		int result = 0;

		//if (v == w)
		if (v.equals(w))
			return 0;

		//if (PyObject_Cmp(v->start, w->start, &result) < 0)
		result = v.start.__cmp__(w.start);
		if (result < 0)
			return -2;
		if (result != 0)
			return result;
		//if (PyObject_Cmp(v->stop, w->stop, &result) < 0)
		result = v.stop.__cmp__(w.stop);
		if (result < 0)
			return -2;
		if (result != 0)
			return result;
		//if (PyObject_Cmp(v->step, w->step, &result) < 0)
		result = v.step.__cmp__(w.step);
		if (result < 0)
			return -2;
		return result;
	}
}
