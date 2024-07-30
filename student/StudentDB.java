package info.kgeorgiy.ja.televnoi.student;

import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.Student;
import info.kgeorgiy.java.advanced.student.StudentQuery;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class StudentDB implements StudentQuery {
    final static Comparator<Student> BY_NAME = Comparator.comparing(Student::getLastName).
            thenComparing(Student::getFirstName).thenComparing(Student::getGroup).thenComparing(Comparator.naturalOrder());

    private <T> List<T> absGet(List<Student> students, Function<Student, T> f) {
        return students.stream().map(f).collect(Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return absGet(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return absGet(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return absGet(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return absGet(students, this::fullName);
    }

    private String fullName(Student s) {
        return String.format("%s %s", s.getFirstName(), s.getLastName());
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return students.stream().map(Student::getFirstName).sorted().collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream().max(Student::compareTo).map(Student::getFirstName).orElse("");
    }

    private List<Student> absSort(Collection<Student> students, Comparator<Student> comparator) {
        return students.stream().sorted(comparator).collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return absSort(students, Student::compareTo);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return absSort(students, BY_NAME);
    }

    private <T> List<Student> absFind(Collection<Student> students, Function<Student, T> f, T el) {
        return students.stream().filter(o -> f.apply(o).equals(el)).sorted(BY_NAME).collect(Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return absFind(students, Student::getFirstName, name);
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return absFind(students, Student::getLastName, name);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return absFind(students, Student::getGroup, group);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return students.stream().filter(o -> o.getGroup().equals(group)).sorted(BY_NAME).collect(
                Collectors.toMap(Student::getLastName, Student::getFirstName, BinaryOperator.minBy(String::compareTo)));
    }
}
