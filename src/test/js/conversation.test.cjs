'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {orderMessages,isComposingEnter} = require('../../main/resources/static/js/conversation.js');
test('history sorts oldest first with ID tie breaker', () => {
  const messages = [
    {id:'b',createdAt:'2026-01-01T00:00:00Z'},
    {id:'c',createdAt:'2026-01-02T00:00:00Z'},
    {id:'a',createdAt:'2026-01-01T00:00:00Z'}
  ];
  assert.deepEqual(messages.sort(orderMessages).map(m=>m.id),['a','b','c']);
});
test('Enter sends only after Korean IME composition is complete', () => {
  assert.equal(isComposingEnter({key:'Enter',shiftKey:false,isComposing:false,keyCode:13}),true);
  assert.equal(isComposingEnter({key:'Enter',shiftKey:false,isComposing:true,keyCode:13}),false);
  assert.equal(isComposingEnter({key:'Enter',shiftKey:false,isComposing:false,keyCode:229}),false);
});
test('Shift+Enter remains a newline and ordinary keys do not send', () => {
  assert.equal(isComposingEnter({key:'Enter',shiftKey:true}),false);
  assert.equal(isComposingEnter({key:'a'}),false);
});
