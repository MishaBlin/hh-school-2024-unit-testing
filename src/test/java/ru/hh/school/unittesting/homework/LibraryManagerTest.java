package ru.hh.school.unittesting.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryManagerTest {

  @Mock
  private NotificationService notificationService;

  @Mock
  private UserService userService;

  @InjectMocks
  private LibraryManager libraryManager;

  @BeforeEach
  void setUp() {
    libraryManager.addBook("book1", 10);
    lenient().when(userService.isUserActive("user_active")).thenReturn(true);
  }

  private void verifyOneNotification(String userId, String message) {
    verify(notificationService, times(1)).notifyUser(userId, message);
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void testGetCurrentCopies() {
    int copies = libraryManager.getAvailableCopies("book1");
    assertEquals(10, copies);
  }

  @Test
  void testBorrowBookInactiveUser() {
    when(userService.isUserActive("user_inactive")).thenReturn(false);

    boolean borrowResult = libraryManager.borrowBook("book1", "user_inactive");

    verifyOneNotification("user_inactive", "Your account is not active.");

    assertFalse(borrowResult);
  }

  @Test
  void testBorrowBookWithZeroCopies() {
    boolean borrowResult = libraryManager.borrowBook("book25", "user_active");
    verifyNoInteractions(notificationService);
    assertFalse(borrowResult);
  }

  @Test
  void testBorrowBookSuccess() throws NoSuchFieldException, IllegalAccessException {
    boolean borrowResult = libraryManager.borrowBook("book1", "user_active");

    verifyOneNotification("user_active", "You have borrowed the book: book1");

    int copiesLeft = libraryManager.getAvailableCopies("book1");

    Field borrowedBooksField = libraryManager.getClass().getDeclaredField("borrowedBooks");
    borrowedBooksField.setAccessible(true);

    @SuppressWarnings("unchecked")
    Map<String, String> borrowedBooks = (Map<String, String>) borrowedBooksField.get(libraryManager);

    assertTrue(borrowResult);
    assertEquals(9, copiesLeft);
    assertEquals("user_active", borrowedBooks.get("book1"));
  }

  @Test
  void testReturnBookWhenNoSuchBorrowedBook() {
    boolean returnResult = libraryManager.returnBook("book25", "user1");
    assertFalse(returnResult);
  }

  @Test
  void testReturnBookWhenNoSuchBorrowedBookForTheGivenUser() {
    boolean borrowResult = libraryManager.borrowBook("book1", "user_active");
    assertTrue(borrowResult);

    boolean returnResult = libraryManager.returnBook("book1", "user2");
    assertFalse(returnResult);
  }

  @Test
  void testReturnBookSuccess() throws NoSuchFieldException, IllegalAccessException {
    boolean borrowResult = libraryManager.borrowBook("book1", "user_active");
    assertTrue(borrowResult);

    boolean returnResult = libraryManager.returnBook("book1", "user_active");
    assertTrue(returnResult);

    Field borrowedBooksField = libraryManager.getClass().getDeclaredField("borrowedBooks");
    borrowedBooksField.setAccessible(true);

    @SuppressWarnings("unchecked")
    Map<String, String> borrowedBooks = (Map<String, String>) borrowedBooksField.get(libraryManager);
    assertFalse(borrowedBooks.containsKey("book1"));

    int copiesLeft = libraryManager.getAvailableCopies("book1");
    assertEquals(10, copiesLeft);

    verify(notificationService, times(1))
        .notifyUser("user_active", "You have returned the book: book1");
  }

  @Test
  void testDynamicLateFeeThrowsIllegalArgumentException() {
    var exception = assertThrows(
        IllegalArgumentException.class,
        () -> libraryManager.calculateDynamicLateFee(-1, false, false)
    );
    assertEquals("Overdue days cannot be negative.", exception.getMessage());
  }

  static Stream<Arguments> provideLateFeeScenarios() {
    return Stream.of(
        Arguments.of(10, false, false, 5.0),
        Arguments.of(13, true, false, 9.75),
        Arguments.of(15, false, true, 6.0),
        Arguments.of(14, true, true, 8.4)
    );
  }

  @ParameterizedTest
  @MethodSource("provideLateFeeScenarios")
  void testDynamicLateFee(int overdueDays, boolean isBestSeller, boolean isPremiumMember, double expectedFee) {
    double feeResult = libraryManager.calculateDynamicLateFee(overdueDays, isBestSeller, isPremiumMember);
    assertEquals(expectedFee, feeResult);
  }

}