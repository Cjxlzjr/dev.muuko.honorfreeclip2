package dev.muuko.honorfreeclip2;

import java.lang.reflect.Method;
import java.util.List;

public final class LifecycleHookResolverSelfTest {
    static class Boundary { protected void onResume() {} protected void onPostResume() {} }
    static class OldHost extends Boundary { void c0() {} @Override protected void onResume() {} }
    static class RenamedHost extends Boundary { void z19() {} void onResume(int ignored) {} }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        List<Method> old = LifecycleHookResolver.resumeMethods(OldHost.class, Boundary.class);
        check(old.size() == 2 && old.get(0).getDeclaringClass() == OldHost.class);
        List<Method> renamed = LifecycleHookResolver.resumeMethods(RenamedHost.class, Boundary.class);
        check(renamed.size() == 2 && renamed.get(0).getDeclaringClass() == Boundary.class);
        check(renamed.stream().allMatch(m -> m.getParameterCount() == 0
                && m.getReturnType() == void.class && !m.getName().equals("c0")));
        check(LifecycleHookResolver.resumeMethods(String.class, Boundary.class).isEmpty());
        check(LifecycleHookResolver.resumeMethods(null, Boundary.class).isEmpty());
        System.out.println("PASS lifecycle selection: overrides, inherited methods, renamed initializer, unrelated class");
    }
}
