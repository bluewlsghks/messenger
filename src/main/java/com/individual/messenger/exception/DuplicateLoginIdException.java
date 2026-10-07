package com.individual.messenger.exception;

/** A conflict is distinct from malformed registration input. */
public class DuplicateLoginIdException extends IllegalArgumentException {
    public DuplicateLoginIdException() { super("이미 존재하는 ID 입니다."); }
    public DuplicateLoginIdException(Throwable cause) { super("이미 존재하는 ID 입니다.", cause); }
}
