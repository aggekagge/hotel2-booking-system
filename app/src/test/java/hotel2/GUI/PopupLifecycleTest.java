package hotel2.GUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Opt in with HOTEL2_UI_TESTS=1 on a machine with a graphical desktop.
@EnabledIfEnvironmentVariable(named = "HOTEL2_UI_TESTS", matches = "1")
class PopupLifecycleTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        var ready = new CompletableFuture<Void>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.complete(null);
        });
        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void nestedMessageBelongsToGuestForm() throws Exception {
        withGuestDialog(context -> {
            queue(context.failure(), () -> {
                Stage message = findMessage("Popup regression");
                assertSame(context.guest(), message.getOwner());
                assertEquals(Modality.WINDOW_MODAL, message.getModality());
                dismissMessage(message);
            });
            context.manager().ShowAlert("Popup regression", "Please check the guest details.");
            assertTrue(context.guest().isShowing(), "Dismissing a message must leave the guest form open.");
            context.guest().close();
        });
    }

    @Test
    void closingMessageRestoresGuestAsCurrentPopup() throws Exception {
        withGuestDialog(context -> {
            queue(context.failure(), () -> dismissMessage(findMessage("Popup regression")));
            context.manager().ShowAlert("Popup regression", "Guest saved.");
            context.manager().closeCurrentPopup();
            assertFalse(context.guest().isShowing(), "After its message closes, closing the popup must close the guest form.");
        });
    }

    @Test
    void invalidEmailCanBeDismissedWithoutLosingGuestDetails() throws Exception {
        withGuestDialog(context -> {
            var form = context.guest().getScene().getRoot();
            ((TextField) form.lookup("#NEWcustFirstName")).setText("Test");
            ((TextField) form.lookup("#NEWcustSurName")).setText("Guest");
            ((TextField) form.lookup("#NEWcustPhone")).setText("0701234567");
            var email = (TextField) form.lookup("#NEWcustMail");
            email.setText("invalid-email");
            ((ChoiceBox<?>) form.lookup("#NEWcustPayment")).getSelectionModel().selectFirst();
            queue(context.failure(), () -> {
                Stage message = findMessage("Ogiltig mailadress");
                assertSame(context.guest(), message.getOwner());
                dismissMessage(message);
            });
            ((Button) form.lookup("#ButtonCreate")).fire();
            assertTrue(context.guest().isShowing());
            assertEquals("invalid-email", email.getText());
            assertEquals("Test", ((TextField) form.lookup("#NEWcustFirstName")).getText());
            context.manager().closeCurrentPopup();
        });
    }

    private void withGuestDialog(Consumer<DialogContext> action) throws Exception {
        var complete = new CompletableFuture<Void>();
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            try {
                Stage main = new Stage();
                SceneManager manager = new SceneManager(main);
                main.show();
                queue(failure, () -> {
                    Stage guest = Window.getWindows().stream()
                        .filter(window -> window instanceof Stage)
                        .map(window -> (Stage) window)
                        .filter(window -> "Skapar ny kund".equals(window.getTitle()))
                        .findFirst().orElseThrow();
                    action.accept(new DialogContext(manager, guest, failure));
                });
                manager.createNewCustomerPopup();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                closeWindows();
                if (failure.get() == null) complete.complete(null);
                else complete.completeExceptionally(failure.get());
            }
        });
        complete.get(15, TimeUnit.SECONDS);
    }

    private static Stage findMessage(String expectedTitle) {
        return Window.getWindows().stream()
            .filter(window -> window instanceof Stage && window.getScene() != null)
            .map(window -> (Stage) window)
            .filter(window -> window.getScene().getRoot().lookup("#titleLabel") instanceof Label title
                && expectedTitle.equals(title.getText()))
            .findFirst().orElseThrow();
    }

    private static void dismissMessage(Stage message) {
        message.getScene().getRoot().lookupAll(".button").stream()
            .filter(node -> node instanceof Button button && "Färdig".equals(button.getText()))
            .map(node -> (Button) node).findFirst().orElseThrow().fire();
    }

    private static void queue(AtomicReference<Throwable> failure, Runnable action) {
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                closeWindows();
            }
        });
    }

    private static void closeWindows() {
        var windows = new ArrayList<>(Window.getWindows());
        for (int index = windows.size() - 1; index >= 0; index--) windows.get(index).hide();
    }

    private record DialogContext(SceneManager manager, Stage guest, AtomicReference<Throwable> failure) {}
}
